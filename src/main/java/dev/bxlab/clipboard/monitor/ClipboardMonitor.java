package dev.bxlab.clipboard.monitor;

import dev.bxlab.clipboard.monitor.detector.ChangeDetector;
import dev.bxlab.clipboard.monitor.detector.OwnershipDetector;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;
import dev.bxlab.clipboard.monitor.exception.ClipboardUnavailableException;
import dev.bxlab.clipboard.monitor.internal.ClipboardAccessor;
import dev.bxlab.clipboard.monitor.internal.OwnContentTracker;
import dev.bxlab.clipboard.monitor.transferable.FilesTransferable;
import dev.bxlab.clipboard.monitor.transferable.ImageTransferable;
import dev.bxlab.clipboard.monitor.util.HashUtils;
import dev.bxlab.clipboard.monitor.util.TextUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * System clipboard monitor with real-time change detection.
 * <p>
 * Detects clipboard changes using configurable detection strategies and notifies
 * registered listeners. Supports multiple listeners with isolation - each listener
 * runs in its own virtual thread, so a slow or failing listener doesn't affect others.
 *
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * try (ClipboardMonitor monitor = ClipboardMonitor.builder()
 *         .detector(PollingDetector.defaults())
 *         .listener(content -> System.out.println("Change: " + content.type()))
 *         .build()) {
 *     monitor.start();
 *     // ... application logic
 * }
 * }</pre>
 *
 * <h2>Pattern Matching (Java 21)</h2>
 * <pre>{@code
 * ClipboardMonitor monitor = ClipboardMonitor.builder()
 *     .detector(PollingDetector.defaults())
 *     .listener(content -> {
 *         switch (content) {
 *             case TextContent t -> System.out.println("Text: " + t.text());
 *             case ImageContent i -> System.out.println("Image: " + i.width() + "x" + i.height());
 *             case FilesContent f -> System.out.println("Files: " + f.files().size());
 *             case UnknownContent _ -> System.out.println("Unknown");
 *         }
 *     })
 *     .build();
 * }</pre>
 *
 * <h2>Bidirectional Sync</h2>
 * <pre>{@code
 * // Content written via write() is automatically tracked to prevent loops
 * monitor.write("text from remote");
 * }</pre>
 *
 * @see PollingDetector
 * @see OwnershipDetector
 * @see ClipboardContent
 */
@Slf4j
public final class ClipboardMonitor implements AutoCloseable {

    private static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(50);

    private final ChangeDetector detector;
    private final List<ClipboardListener> listeners;
    private final Duration debounce;
    private final boolean notifyOnStart;

    private final Clipboard clipboard;
    private final OwnContentTracker ownContentTracker;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Instant startTime;

    // Dedicated lock object for synchronization (never use synchronized(this))
    private final Object debounceLock = new Object();

    // For debounce: pending hash pattern
    private final AtomicReference<String> lastNotifiedHash = new AtomicReference<>("");
    private volatile String pendingHash = null;
    private volatile long pendingStartNanos = 0;

    // Watch loop thread
    private final AtomicReference<Thread> watchThread = new AtomicReference<>();

    private ClipboardMonitor(Builder builder) {
        this.detector = builder.detector;
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);
        this.debounce = builder.debounce;
        this.notifyOnStart = builder.notifyOnStart;

        this.clipboard = ClipboardAccessor.getClipboard();
        this.ownContentTracker = OwnContentTracker.create();

        // Initialize detector with our callback
        initializeDetector();
    }

    private void initializeDetector() {
        Consumer<String> callback = this::onHashChange;
        if (detector instanceof PollingDetector pd) {
            pd.initialize(callback);
        } else if (detector instanceof OwnershipDetector od) {
            od.initialize(callback);
        }
    }

    /**
     * Creates a new builder for configuring a ClipboardMonitor.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts clipboard monitoring.
     * <p>
     * Safe to call multiple times (no-op if already running).
     *
     * @throws IllegalStateException if the monitor has been closed
     */
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("Monitor has been closed");
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("Monitor already running");
            return;
        }

        startTime = Instant.now();

        // Capture the initial hash
        try {
            Transferable contents = ClipboardAccessor.getContents();
            if (contents != null) {
                String initialHash = ClipboardAccessor.calculateHash(contents);
                lastNotifiedHash.set(initialHash);
                log.debug("Initial clipboard hash: {}", TextUtils.truncate(initialHash));

                if (notifyOnStart) {
                    ClipboardContent content = readContent(initialHash);
                    notifyListeners(content);
                }
            }
        } catch (Exception e) {
            log.warn("Could not capture initial clipboard state: {}", e.getMessage());
        }

        // Start detector
        detector.start();

        // Start a watch loop for debounce processing
        Thread thread = Thread.ofVirtual()
                .name("clipboard-watch-loop")
                .start(this::watchLoop);
        watchThread.set(thread);

        log.info("ClipboardMonitor started with {} detector", detector.getClass().getSimpleName());
    }

    /**
     * Closes the monitor and releases all resources.
     * <p>
     * Safe to call multiple times. Idempotent.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        running.set(false);

        // Stop detector
        detector.stop();

        // Stop watch thread
        Thread thread = watchThread.get();
        if (thread != null) {
            thread.interrupt();
        }

        // Clear tracker
        ownContentTracker.clear();

        log.info("ClipboardMonitor closed");
    }

    /**
     * Returns whether the monitor is currently running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Writes text to the clipboard.
     * <p>
     * The content is automatically tracked to prevent notification loops
     * in bidirectional sync scenarios.
     *
     * @param text text to write
     * @throws NullPointerException if text is null
     */
    public void write(String text) {
        Objects.requireNonNull(text, "text cannot be null");

        String hash = HashUtils.sha256(text);
        ownContentTracker.markOwn(hash);

        StringSelection selection = new StringSelection(text);
        clipboard.setContents(selection, null);

        updateDetectorHash(hash, selection);
        log.debug("Wrote text to clipboard ({} chars)", text.length());
    }

    /**
     * Writes an image to the clipboard.
     * <p>
     * The content is automatically tracked to prevent notification loops
     * in bidirectional sync scenarios.
     *
     * @param image image to write
     * @throws NullPointerException if the image is null
     */
    public void write(BufferedImage image) {
        Objects.requireNonNull(image, "image cannot be null");

        String hash = HashUtils.sha256(image);
        ownContentTracker.markOwn(hash);

        ImageTransferable transferable = new ImageTransferable(image);
        clipboard.setContents(transferable, null);

        updateDetectorHash(hash, transferable);
        log.debug("Wrote image to clipboard ({}x{})", image.getWidth(), image.getHeight());
    }

    /**
     * Writes a file list to the clipboard.
     * <p>
     * The content is automatically tracked to prevent notification loops
     * in bidirectional sync scenarios.
     *
     * @param files files to write
     * @throws NullPointerException if files are null
     */
    public void write(List<File> files) {
        Objects.requireNonNull(files, "files cannot be null");

        String hash = HashUtils.sha256(files);
        ownContentTracker.markOwn(hash);

        FilesTransferable transferable = new FilesTransferable(files);
        clipboard.setContents(transferable, null);

        updateDetectorHash(hash, transferable);
        log.debug("Wrote files to clipboard ({} files)", files.size());
    }

    private void updateDetectorHash(String hash, Transferable transferable) {
        if (detector instanceof PollingDetector pd) {
            pd.updateLastHash(hash);
        } else if (detector instanceof OwnershipDetector od) {
            od.retakeOwnership(transferable);
        }
        // Also update our last notified hash to prevent re-notification
        lastNotifiedHash.set(hash);
    }

    /**
     * Reads the current clipboard content.
     *
     * @return current clipboard content
     * @throws ClipboardUnavailableException if the clipboard cannot be read
     */
    public ClipboardContent read() {
        try {
            Transferable contents = ClipboardAccessor.getContents();
            if (contents == null) {
                return new UnknownContent(HashUtils.sha256(new byte[0]), Instant.now());
            }
            String hash = ClipboardAccessor.calculateHash(contents);
            return readContent(hash);
        } catch (Exception e) {
            throw new ClipboardUnavailableException("Failed to read clipboard", e);
        }
    }

    /**
     * Tries to read the current clipboard content.
     *
     * @return current content or empty if unavailable
     */
    public Optional<ClipboardContent> tryRead() {
        try {
            return Optional.of(read());
        } catch (Exception e) {
            log.debug("Could not read clipboard: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Called by detector when a hash change is detected.
     */
    private void onHashChange(String newHash) {
        if (!running.get()) {
            return;
        }

        // Check if this is our own content
        if (ownContentTracker.isOwn(newHash)) {
            log.debug("Ignoring own content: {}", TextUtils.truncate(newHash));
            return;
        }

        // Update pending hash for debounce processing
        synchronized (debounceLock) {
            if (pendingHash == null || !pendingHash.equals(newHash)) {
                pendingHash = newHash;
                pendingStartNanos = System.nanoTime();
                log.debug("New pending hash: {}", TextUtils.truncate(newHash));
            }
        }
    }

    /**
     * Watch loop that processes pending changes with debounce.
     */
    @SuppressWarnings({"BusyWait", "java:S1181"}) // Intentional polling loop for debounce processing
    private void watchLoop() {
        long debounceNanos = debounce.toNanos();

        while (running.get()) {
            try {
                String currentPending;
                long pendingStart;

                synchronized (debounceLock) {
                    currentPending = pendingHash;
                    pendingStart = pendingStartNanos;
                }

                if (currentPending != null && !currentPending.equals(lastNotifiedHash.get())) {
                    long elapsed = System.nanoTime() - pendingStart;

                    if (elapsed >= debounceNanos) {
                        processPendingChange(currentPending);
                    }
                }

                // Sleep a short interval before checking again
                Thread.sleep(10);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Handle recoverable exceptions without dying
                log.error("Error in watch loop", e);
            } catch (Error e) {
                // Log fatal errors but rethrow - these are unrecoverable
                log.error("Fatal error in watch loop", e);
                throw e;
            }
        }

        log.debug("Watch loop ended");
    }

    private void processPendingChange(String hash) {
        try {
            // Read content
            ClipboardContent content = readContent(hash);

            // Post-read verification: ensure content hasn't changed during read
            if (!content.hash().equals(hash)) {
                log.debug("Content changed during read (expected: {}, got: {}), will retry",
                        TextUtils.truncate(hash), TextUtils.truncate(content.hash()));
                synchronized (debounceLock) {
                    pendingHash = null; // Reset to detect new change
                }
                return;
            }

            // Successfully read stable content
            lastNotifiedHash.set(hash);
            synchronized (debounceLock) {
                pendingHash = null;
            }

            notifyListeners(content);

        } catch (Exception e) {
            log.error("Error processing clipboard change", e);
            synchronized (debounceLock) {
                pendingHash = null; // Reset on error
            }
        }
    }

    private ClipboardContent readContent(String expectedHash) {
        Instant now = Instant.now();

        // Try to read text
        String text = ClipboardAccessor.readText();
        if (text != null) {
            String hash = HashUtils.sha256(text);
            return new TextContent(text, hash, now);
        }

        // Try to read image
        BufferedImage image = ClipboardAccessor.readImage();
        if (image != null) {
            String hash = HashUtils.sha256(image);
            return ImageContent.of(image, hash, now);
        }

        // Try to read files
        List<File> files = ClipboardAccessor.readFiles();
        if (!files.isEmpty()) {
            String hash = HashUtils.sha256(files);
            return FilesContent.of(files, hash, now);
        }

        // Unknown content
        return new UnknownContent(expectedHash, now);
    }

    private void notifyListeners(ClipboardContent content) {
        for (int i = 0; i < listeners.size(); i++) {
            final ClipboardListener listener = listeners.get(i);
            Thread.ofVirtual()
                    .name("clipboard-listener-" + i)
                    .start(() -> {
                        try {
                            listener.onClipboardChange(content);
                        } catch (Exception e) {
                            listener.onError(e);
                        }
                    });
        }
    }

    /**
     * Builder for creating ClipboardMonitor instances.
     */
    public static final class Builder {
        private ChangeDetector detector;
        private final List<ClipboardListener> listeners = new ArrayList<>();
        private Duration debounce = DEFAULT_DEBOUNCE;
        private boolean notifyOnStart = false;

        private Builder() {
        }

        /**
         * Sets the change detection strategy.
         * <p>
         * This is required. Use either {@link PollingDetector} or {@link OwnershipDetector}.
         *
         * @param detector the detector to use
         * @return this builder
         * @throws NullPointerException if detector is null
         * @see PollingDetector#defaults()
         * @see OwnershipDetector#defaults()
         */
        public Builder detector(ChangeDetector detector) {
            this.detector = Objects.requireNonNull(detector, "detector cannot be null");
            return this;
        }

        /**
         * Adds a listener to receive clipboard change notifications.
         * <p>
         * Multiple listeners can be added. Each listener runs in its own
         * virtual thread for isolation.
         *
         * @param listener listener to add
         * @return this builder
         * @throws NullPointerException if the listener is null
         */
        public Builder listener(ClipboardListener listener) {
            Objects.requireNonNull(listener, "listener cannot be null");
            this.listeners.add(listener);
            return this;
        }

        /**
         * Sets the debounce duration.
         * <p>
         * Groups rapid consecutive changes into a single notification.
         * Default is 50ms.
         *
         * @param debounce debounce duration (must be non-negative)
         * @return this builder
         * @throws NullPointerException     if debounce is null
         * @throws IllegalArgumentException if debounce is negative
         */
        public Builder debounce(Duration debounce) {
            Objects.requireNonNull(debounce, "debounce cannot be null");
            if (debounce.isNegative()) {
                throw new IllegalArgumentException("debounce cannot be negative: " + debounce);
            }
            this.debounce = debounce;
            return this;
        }

        /**
         * Whether to notify the initial clipboard content when starting.
         * <p>
         * If false (default), only changes after start are notified.
         * If true, the current clipboard content is notified immediately.
         *
         * @param notify true to notify initial content
         * @return this builder
         */
        public Builder notifyOnStart(boolean notify) {
            this.notifyOnStart = notify;
            return this;
        }

        /**
         * Builds the ClipboardMonitor instance.
         *
         * @return new ClipboardMonitor instance
         * @throws IllegalStateException if the detector is not set or no listeners configured
         */
        public ClipboardMonitor build() {
            if (detector == null) {
                throw new IllegalStateException("detector is required - use detector(PollingDetector.defaults()) or detector(OwnershipDetector.defaults())");
            }
            if (listeners.isEmpty()) {
                throw new IllegalStateException("At least one listener is required");
            }
            return new ClipboardMonitor(this);
        }
    }
}
