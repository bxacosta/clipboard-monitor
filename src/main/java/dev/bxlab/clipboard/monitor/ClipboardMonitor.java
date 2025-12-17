package dev.bxlab.clipboard.monitor;

import dev.bxlab.clipboard.monitor.exception.ClipboardChangedException;
import dev.bxlab.clipboard.monitor.internal.AntiLoopGuard;
import dev.bxlab.clipboard.monitor.internal.ContentReader;
import dev.bxlab.clipboard.monitor.internal.Stats;
import dev.bxlab.clipboard.monitor.internal.StatsCollector;
import dev.bxlab.clipboard.monitor.internal.detector.OwnershipDetector;
import dev.bxlab.clipboard.monitor.internal.detector.PollingDetector;
import dev.bxlab.clipboard.monitor.transferable.FileListTransferable;
import dev.bxlab.clipboard.monitor.transferable.ImageTransferable;
import dev.bxlab.clipboard.monitor.util.HashUtils;
import dev.bxlab.clipboard.monitor.util.LogUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.Toolkit;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * System clipboard monitor.
 * <p>
 * Detects clipboard changes using a combination of ClipboardOwner (for fast detection)
 * and polling (as reliable fallback).
 * <p>
 * Basic usage:
 * <pre>{@code
 * try (ClipboardMonitor monitor = ClipboardMonitor.builder()
 *         .listener(content -> System.out.println("Change: " + content.getType()))
 *         .build()) {
 *     monitor.start();
 *     // ... application logic
 * }
 * }</pre>
 * <p>
 * For bidirectional sync:
 * <pre>{@code
 * monitor.setContent("text from remote");  // Auto-marked to prevent loop
 * }</pre>
 */
@Slf4j
public final class ClipboardMonitor implements AutoCloseable {

    private static final long DEFAULT_MAX_CONTENT_SIZE = 100_000_000;
    private static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofMillis(500);
    private static final long DEFAULT_DEBOUNCE_MS = 100;

    private final List<ClipboardListener> listeners;
    private final long maxContentSize;
    private final Duration pollingInterval;
    private final long debounceMs;
    private final boolean ownershipEnabled;
    private final boolean notifyInitialContent;

    private final Clipboard clipboard;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService callbackExecutor;
    private final ContentReader contentReader;
    private final AntiLoopGuard antiLoopGuard;
    private final StatsCollector statsCollector;

    private OwnershipDetector ownershipDetector;
    private PollingDetector pollingDetector;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String lastProcessedHash = "";
    private ScheduledFuture<?> debounceTask;
    private final Object debounceLock = new Object();

    private ClipboardMonitor(Builder builder) {
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);
        this.maxContentSize = builder.maxContentSize;
        this.pollingInterval = builder.pollingInterval;
        this.debounceMs = builder.debounceMs;
        this.ownershipEnabled = builder.ownershipEnabled;
        this.notifyInitialContent = builder.notifyInitialContent;

        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "clipboard-monitor-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.callbackExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "clipboard-monitor-callback");
            t.setDaemon(true);
            return t;
        });

        this.contentReader = new ContentReader(clipboard, maxContentSize);
        this.antiLoopGuard = new AntiLoopGuard(scheduler);
        this.statsCollector = new StatsCollector();
    }

    /**
     * Starts clipboard monitoring.
     * Safe to call multiple times (no-op if already running).
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Monitor already running");
            return;
        }

        try {
            Transferable current = clipboard.getContents(null);
            String initialHash = contentReader.calculateHash(current);

            if (notifyInitialContent) {
                log.debug("Will notify initial clipboard content");
                onClipboardChange(current);
            } else {
                lastProcessedHash = initialHash;
                log.debug("Initial clipboard hash captured (will be ignored): {}",
                        LogUtils.truncateHash(initialHash));
            }
        } catch (Exception e) {
            log.warn("Could not capture initial clipboard hash: {}", e.getMessage());
        }

        if (ownershipEnabled) {
            ownershipDetector = new OwnershipDetector(clipboard, scheduler, this::onClipboardChange);
            ownershipDetector.start();
        }

        pollingDetector = new PollingDetector(
                clipboard,
                scheduler,
                this::onClipboardChange,
                contentReader,
                pollingInterval
        );
        pollingDetector.start();

        log.info("ClipboardMonitor started (ownership={}, polling={}ms)", ownershipEnabled, pollingInterval.toMillis());
    }

    /**
     * Stops clipboard monitoring.
     * Safe to call multiple times.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            log.debug("Monitor already stopped");
            return;
        }

        if (ownershipDetector != null) {
            ownershipDetector.stop();
        }
        if (pollingDetector != null) {
            pollingDetector.stop();
        }

        synchronized (debounceLock) {
            if (debounceTask != null) {
                debounceTask.cancel(false);
                debounceTask = null;
            }
        }

        log.info("ClipboardMonitor stopped");
    }

    /**
     * Closes the monitor and releases all resources.
     */
    @Override
    public void close() {
        stop();
        scheduler.shutdown();
        callbackExecutor.shutdown();

        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!callbackExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                callbackExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            callbackExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("ClipboardMonitor closed");
    }

    /**
     * @return true if monitor is running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Writes text to clipboard.
     * Content is automatically marked as "own" to prevent bidirectional sync loops.
     *
     * @param text text to write
     * @throws NullPointerException if text is null
     */
    public void setContent(String text) {
        Objects.requireNonNull(text, "text cannot be null");

        StringSelection selection = new StringSelection(text);
        String hash = HashUtils.sha256(text);

        setContentInternal(selection, hash);
        log.debug("Set clipboard text content ({} chars)", text.length());
    }

    /**
     * Writes an image to clipboard.
     * Content is automatically marked as "own" to prevent bidirectional sync loops.
     *
     * @param image image to write
     * @throws NullPointerException if image is null
     */
    public void setContent(BufferedImage image) {
        Objects.requireNonNull(image, "image cannot be null");

        ImageTransferable transferable = new ImageTransferable(image);
        String hash = HashUtils.hashImage(image);

        setContentInternal(transferable, hash);
        log.debug("Set clipboard image content ({}x{})", image.getWidth(), image.getHeight());
    }

    /**
     * Writes a file list to clipboard.
     * Content is automatically marked as "own" to prevent bidirectional sync loops.
     *
     * @param files files to write
     * @throws NullPointerException if files is null
     */
    public void setContent(List<File> files) {
        Objects.requireNonNull(files, "files cannot be null");

        FileListTransferable transferable = new FileListTransferable(files);
        String hash = HashUtils.hashFileList(files);

        setContentInternal(transferable, hash);
        log.debug("Set clipboard file list content ({} files)", files.size());
    }

    private void setContentInternal(Transferable transferable, String hash) {
        antiLoopGuard.markAsOwn(hash);
        clipboard.setContents(transferable, null);

        if (pollingDetector != null) {
            pollingDetector.updateLastHash(hash);
        }
        if (ownershipDetector != null) {
            ownershipDetector.retakeOwnership(transferable);
        }
    }

    /**
     * Reads current clipboard content without waiting for changes.
     *
     * @return current content or empty if unavailable
     */
    public Optional<ClipboardContent> getCurrentContent() {
        try {
            return Optional.of(contentReader.read());
        } catch (Exception e) {
            log.error("Error reading current clipboard content", e);
            return Optional.empty();
        }
    }

    /**
     * Returns an immutable snapshot of monitor statistics.
     *
     * @return current statistics
     */
    public Stats getStats() {
        return statsCollector.snapshot();
    }

    /**
     * Adds a listener.
     *
     * @param listener listener to add
     * @throws NullPointerException if listener is null
     */
    public void addListener(ClipboardListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener listener to remove
     * @return true if removed
     */
    public boolean removeListener(ClipboardListener listener) {
        return listeners.remove(listener);
    }

    private void onClipboardChange(Transferable transferable) {
        if (!running.get()) {
            return;
        }

        String hash = contentReader.calculateHash(transferable);

        if (antiLoopGuard.isOwnContent(hash)) {
            log.debug("Ignoring own content: {}", LogUtils.truncateHash(hash));
            return;
        }

        if (hash.equals(lastProcessedHash)) {
            log.debug("Ignoring duplicate content (same hash)");
            return;
        }

        synchronized (debounceLock) {
            if (debounceTask != null) {
                debounceTask.cancel(false);
            }

            debounceTask = scheduler.schedule(() -> {
                processChange(hash);
            }, debounceMs, TimeUnit.MILLISECONDS);
        }
    }

    private void processChange(String hash) {
        if (!running.get()) {
            return;
        }

        if (hash.equals(lastProcessedHash)) {
            log.debug("Hash already processed after debounce, skipping");
            return;
        }

        try {
            ClipboardContent content = contentReader.read();

            lastProcessedHash = hash;
            statsCollector.recordChange();

            notifyListeners(content);

        } catch (ClipboardChangedException e) {
            log.debug("Clipboard changed during processing, will be detected on next cycle");
        } catch (Exception e) {
            statsCollector.recordError();
            log.error("Error processing clipboard change", e);
            notifyError(e);
        }
    }

    private void notifyListeners(ClipboardContent content) {
        callbackExecutor.submit(() -> {
            for (ClipboardListener listener : listeners) {
                try {
                    listener.onClipboardChange(content);
                } catch (Exception e) {
                    log.error("Error in clipboard listener", e);
                    try {
                        listener.onError(e);
                    } catch (Exception e2) {
                        log.error("Error in listener error handler", e2);
                    }
                }
            }
        });
    }

    private void notifyError(Exception error) {
        callbackExecutor.submit(() -> {
            for (ClipboardListener listener : listeners) {
                try {
                    listener.onError(error);
                } catch (Exception e) {
                    log.error("Error in listener error handler", e);
                }
            }
        });
    }

    /**
     * Creates a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ClipboardMonitor instances.
     */
    public static final class Builder {
        private final List<ClipboardListener> listeners = new ArrayList<>();
        private long maxContentSize = DEFAULT_MAX_CONTENT_SIZE;
        private Duration pollingInterval = DEFAULT_POLLING_INTERVAL;
        private long debounceMs = DEFAULT_DEBOUNCE_MS;
        private boolean ownershipEnabled = true;
        private boolean notifyInitialContent = false;

        private Builder() {
        }

        /**
         * Adds a listener to receive change notifications.
         *
         * @param listener listener to add
         * @return this builder
         * @throws NullPointerException if listener is null
         */
        public Builder listener(ClipboardListener listener) {
            Objects.requireNonNull(listener, "listener cannot be null");
            this.listeners.add(listener);
            return this;
        }

        /**
         * Maximum content size to process (default: 100MB).
         * Larger content throws ContentTooLargeException.
         *
         * @param maxContentSize maximum size in bytes
         * @return this builder
         * @throws IllegalArgumentException if maxContentSize is not positive
         */
        public Builder maxContentSize(long maxContentSize) {
            if (maxContentSize <= 0) {
                throw new IllegalArgumentException("maxContentSize must be positive");
            }
            this.maxContentSize = maxContentSize;
            return this;
        }

        /**
         * Polling interval (default: 500ms).
         * Lower interval = lower latency but higher CPU usage.
         *
         * @param pollingInterval interval between polls
         * @return this builder
         * @throws NullPointerException     if pollingInterval is null
         * @throws IllegalArgumentException if pollingInterval is not positive
         */
        public Builder pollingInterval(Duration pollingInterval) {
            Objects.requireNonNull(pollingInterval, "pollingInterval cannot be null");
            if (pollingInterval.isNegative() || pollingInterval.isZero()) {
                throw new IllegalArgumentException("pollingInterval must be positive");
            }
            this.pollingInterval = pollingInterval;
            return this;
        }

        /**
         * Debounce time (default: 100ms).
         * Groups rapid consecutive changes into a single notification.
         *
         * @param debounceMs time in milliseconds
         * @return this builder
         * @throws IllegalArgumentException if debounceMs is negative
         */
        public Builder debounceMs(long debounceMs) {
            if (debounceMs < 0) {
                throw new IllegalArgumentException("debounceMs cannot be negative");
            }
            this.debounceMs = debounceMs;
            return this;
        }

        /**
         * Enables/disables ownership detector (default: true).
         * Disable if it causes issues on your system.
         *
         * @param enabled true to enable
         * @return this builder
         */
        public Builder ownershipEnabled(boolean enabled) {
            this.ownershipEnabled = enabled;
            return this;
        }

        /**
         * Notifies initial clipboard content on start (default: false).
         * <p>
         * If false (default), only NEW changes after start are notified.
         * If true, current clipboard content is notified as first change.
         *
         * @param notify true to notify initial content
         * @return this builder
         */
        public Builder notifyInitialContent(boolean notify) {
            this.notifyInitialContent = notify;
            return this;
        }

        /**
         * Builds the ClipboardMonitor.
         *
         * @return new ClipboardMonitor instance
         * @throws IllegalStateException if no listeners configured
         */
        public ClipboardMonitor build() {
            if (listeners.isEmpty()) {
                throw new IllegalStateException("At least one listener is required");
            }
            return new ClipboardMonitor(this);
        }
    }
}
