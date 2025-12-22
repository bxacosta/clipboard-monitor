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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    private final Object debounceLock = new Object();

    private final AtomicReference<String> lastNotifiedHash = new AtomicReference<>("");
    private volatile ClipboardContent pendingContent = null;
    private volatile long pendingStartNanos = 0;

    private final AtomicReference<Thread> watchThread = new AtomicReference<>();

    private ClipboardMonitor(Builder builder) {
        this.detector = builder.detector;
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);
        this.debounce = builder.debounce;
        this.notifyOnStart = builder.notifyOnStart;

        this.clipboard = ClipboardAccessor.getClipboard();
        this.ownContentTracker = OwnContentTracker.create();
    }

    /**
     * Creates a new builder.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts clipboard monitoring.
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

        String initialHash = "";
        try {
            ClipboardContent initialContent = ClipboardAccessor.readContent();
            initialHash = initialContent.hash();
            lastNotifiedHash.set(initialHash);
            log.debug("Initial clipboard hash: {}", TextUtils.truncate(initialHash));

            if (notifyOnStart) {
                notifyListeners(initialContent);
            }
        } catch (Exception e) {
            log.warn("Could not capture initial clipboard state: {}", e.getMessage());
        }

        detector.start(this::onContentChange, initialHash);

        Thread thread = Thread.ofVirtual()
                .name("clipboard-watch-loop")
                .start(this::watchLoop);
        watchThread.set(thread);

        log.info("ClipboardMonitor started with {} detector", detector.getClass().getSimpleName());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        running.set(false);

        detector.stop();

        Thread thread = watchThread.get();
        if (thread != null) {
            thread.interrupt();
        }

        ownContentTracker.clear();

        log.info("ClipboardMonitor closed");
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Writes text to the clipboard.
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

        updateDetectorAfterWrite(hash, selection);
        log.debug("Wrote text to clipboard ({} chars)", text.length());
    }

    /**
     * Writes an image to the clipboard.
     *
     * @param image image to write
     * @throws NullPointerException if image is null
     */
    public void write(BufferedImage image) {
        Objects.requireNonNull(image, "image cannot be null");

        String hash = HashUtils.sha256(image);
        ownContentTracker.markOwn(hash);

        ImageTransferable transferable = new ImageTransferable(image);
        clipboard.setContents(transferable, null);

        updateDetectorAfterWrite(hash, transferable);
        log.debug("Wrote image to clipboard ({}x{})", image.getWidth(), image.getHeight());
    }

    /**
     * Writes a list of files to the clipboard.
     *
     * @param files files to write
     * @throws NullPointerException if files is null
     */
    public void write(List<File> files) {
        Objects.requireNonNull(files, "files cannot be null");

        String hash = HashUtils.sha256(files);
        ownContentTracker.markOwn(hash);

        FilesTransferable transferable = new FilesTransferable(files);
        clipboard.setContents(transferable, null);

        updateDetectorAfterWrite(hash, transferable);
        log.debug("Wrote files to clipboard ({} files)", files.size());
    }

    private void updateDetectorAfterWrite(String hash, Transferable transferable) {
        switch (detector) {
            case PollingDetector pd -> pd.updateLastHash(hash);
            case OwnershipDetector od -> od.retakeOwnership(transferable);
        }
        lastNotifiedHash.set(hash);
    }

    /**
     * Reads the current clipboard content.
     *
     * @return current clipboard content
     * @throws ClipboardUnavailableException if clipboard cannot be read
     */
    public ClipboardContent read() {
        try {
            return ClipboardAccessor.readContent();
        } catch (Exception e) {
            throw new ClipboardUnavailableException("Failed to read clipboard", e);
        }
    }

    /**
     * Tries to read the current clipboard content.
     *
     * @return current content, or empty if unavailable
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
     * Called by detector when clipboard content changes.
     *
     * @param content the new clipboard content
     */
    private void onContentChange(ClipboardContent content) {
        if (!running.get()) {
            return;
        }

        String hash = content.hash();

        if (ownContentTracker.isOwn(hash)) {
            log.debug("Ignoring own content: {}", TextUtils.truncate(hash));
            return;
        }

        synchronized (debounceLock) {
            if (pendingContent == null || !pendingContent.hash().equals(hash)) {
                pendingContent = content;
                pendingStartNanos = System.nanoTime();
                log.debug("New pending content: {}", TextUtils.truncate(hash));
            }
        }
    }

    @SuppressWarnings("BusyWait")
    private void watchLoop() {
        long debounceNanos = debounce.toNanos();

        while (running.get()) {
            try {
                ClipboardContent currentPending;
                long pendingStart;

                synchronized (debounceLock) {
                    currentPending = pendingContent;
                    pendingStart = pendingStartNanos;
                }

                if (currentPending != null && !currentPending.hash().equals(lastNotifiedHash.get())) {
                    long elapsed = System.nanoTime() - pendingStart;

                    if (elapsed >= debounceNanos) {
                        processPendingChange(currentPending);
                    }
                }

                Thread.sleep(10);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in watch loop", e);
            }
        }

        log.debug("Watch loop ended");
    }

    /**
     * Processes a pending content change after the debounce period.
     *
     * @param content the content to process
     */
    private void processPendingChange(ClipboardContent content) {
        try {
            String expectedHash = content.hash();

            ClipboardContent currentContent = ClipboardAccessor.readContent();
            if (!currentContent.hash().equals(expectedHash)) {
                log.debug("Content changed during debounce (expected: {}, got: {}), using current",
                        TextUtils.truncate(expectedHash), TextUtils.truncate(currentContent.hash()));
                content = currentContent;
            }

            lastNotifiedHash.set(content.hash());
            synchronized (debounceLock) {
                pendingContent = null;
            }

            notifyListeners(content);

        } catch (Exception e) {
            log.error("Error processing clipboard change", e);
            synchronized (debounceLock) {
                pendingContent = null;
            }
        }
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
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {
        private ChangeDetector detector;
        private final List<ClipboardListener> listeners = new ArrayList<>();
        private Duration debounce = DEFAULT_DEBOUNCE;
        private boolean notifyOnStart = false;

        /**
         * Sets the change detection strategy.
         *
         * @param detector the detector to use
         * @return this builder
         */
        public Builder detector(ChangeDetector detector) {
            this.detector = detector;
            return this;
        }

        /**
         * Adds a listener to receive clipboard change notifications.
         *
         * @param listener listener to add
         * @return this builder
         */
        public Builder listener(ClipboardListener listener) {
            this.listeners.add(listener);
            return this;
        }

        /**
         * Sets the debounce duration.
         *
         * @param debounce debounce duration (must be non-negative)
         * @return this builder
         */
        public Builder debounce(Duration debounce) {
            this.debounce = debounce;
            return this;
        }

        /**
         * Whether to notify the initial clipboard content when starting.
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
         * @throws NullPointerException     if debounce is null, or the listener list contains null
         * @throws IllegalArgumentException if debounce is negative
         * @throws IllegalStateException    if detector is not set or no listeners are configured
         */
        public ClipboardMonitor build() {
            if (detector == null) {
                throw new IllegalStateException("detector is required");
            }

            Objects.requireNonNull(debounce, "debounce cannot be null");

            if (listeners.isEmpty()) {
                throw new IllegalStateException("at least one listener is required");
            }

            for (ClipboardListener listener : listeners) {
                Objects.requireNonNull(listener, "the listener list cannot contain null elements");
            }

            if (debounce.isNegative()) {
                throw new IllegalArgumentException("debounce cannot be negative: " + debounce);
            }

            return new ClipboardMonitor(this);
        }
    }
}
