package dev.bxlab.clipboard.monitor.detector;

import dev.bxlab.clipboard.monitor.internal.ClipboardAccessor;
import dev.bxlab.clipboard.monitor.util.TextUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.datatransfer.Transferable;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Detects clipboard changes via periodic polling.
 * <p>
 * This detector periodically reads the clipboard and compares content hashes
 * to detect changes. It is more reliable than {@link OwnershipDetector} but
 * has higher latency (up to the polling interval).
 * <p>
 * Recommended for most use cases due to its reliability across all platforms.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // With defaults (200ms interval)
 * PollingDetector detector = PollingDetector.defaults();
 *
 * // With custom interval
 * PollingDetector detector = PollingDetector.builder()
 *     .interval(Duration.ofMillis(100))
 *     .build();
 * }</pre>
 *
 * @see ChangeDetector
 * @see OwnershipDetector
 */
@Slf4j
public final class PollingDetector implements ChangeDetector {

    /**
     * Default polling interval.
     */
    public static final Duration DEFAULT_INTERVAL = Duration.ofMillis(200);

    private final Duration interval;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Consumer<String>> hashChangeCallback = new AtomicReference<>();
    private final AtomicReference<Thread> pollingThread = new AtomicReference<>();

    private volatile String lastHash = "";

    private PollingDetector(Builder builder) {
        this.interval = builder.interval;
    }

    /**
     * Creates a new builder for configuring a PollingDetector.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a PollingDetector with default settings.
     * <p>
     * Uses a polling interval of 200ms.
     *
     * @return new PollingDetector with defaults
     */
    public static PollingDetector defaults() {
        return builder().build();
    }

    /**
     * Returns the configured polling interval.
     *
     * @return polling interval
     */
    public Duration getInterval() {
        return interval;
    }

    /**
     * Initializes the detector with a callback for hash changes.
     * <p>
     * Called by ClipboardMonitor when the detector is attached.
     * The callback receives the hash of new content when a change is detected.
     *
     * @param callback callback to invoke when clipboard hash changes
     */
    public void initialize(Consumer<String> callback) {
        this.hashChangeCallback.set(Objects.requireNonNull(callback, "callback cannot be null"));
    }

    @Override
    public void start() {
        if (running.getAndSet(true)) {
            log.debug("PollingDetector already running");
            return;
        }

        if (hashChangeCallback.get() == null) {
            throw new IllegalStateException("Detector not initialized. Call initialize() first.");
        }

        // Get initial hash
        try {
            Transferable contents = ClipboardAccessor.getContents();
            if (contents != null) {
                lastHash = ClipboardAccessor.calculateHash(contents);
                log.debug("Initial clipboard hash: {}", TextUtils.truncate(lastHash));
            }
        } catch (Exception e) {
            log.warn("Could not get initial clipboard hash: {}", e.getMessage());
            lastHash = "";
        }

        // Start polling thread (virtual thread)
        pollingThread.set(Thread.ofVirtual()
                .name("clipboard-polling-detector")
                .start(this::pollLoop));

        log.info("PollingDetector started with interval: {}ms", interval.toMillis());
    }

    @Override
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        Thread thread = pollingThread.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
        }

        log.info("PollingDetector stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Updates the last known hash.
     * <p>
     * Called by ClipboardMonitor after writing to clipboard to prevent
     * detecting our own write as an external change.
     *
     * @param hash new hash to remember
     */
    public void updateLastHash(String hash) {
        this.lastHash = hash != null ? hash : "";
        log.debug("Updated last known hash: {}", TextUtils.truncate(this.lastHash));
    }

    @SuppressWarnings("BusyWait") // Not busy-wait: Thread.sleep releases CPU, the pattern is correct for polling
    private void pollLoop() {
        while (running.get()) {
            try {
                poll();
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Polling thread interrupted");
                break;
            } catch (Exception e) {
                log.error("Error in polling loop", e);
                // Continue polling despite errors
            }
        }
    }

    private void poll() {
        try {
            Transferable contents = ClipboardAccessor.getContents();
            if (contents == null) {
                return;
            }

            String currentHash = ClipboardAccessor.calculateHash(contents);
            if (!currentHash.equals(lastHash)) {
                log.debug("Clipboard change detected via polling. Old: {}, New: {}",
                        TextUtils.truncate(lastHash),
                        TextUtils.truncate(currentHash));

                lastHash = currentHash;
                hashChangeCallback.get().accept(currentHash);
            }
        } catch (IllegalStateException e) {
            log.debug("Clipboard busy during poll, will retry next cycle");
        }
    }

    /**
     * Builder for creating PollingDetector instances.
     */
    public static final class Builder {
        private Duration interval = DEFAULT_INTERVAL;

        private Builder() {
        }

        /**
         * Sets the polling interval.
         * <p>
         * Shorter intervals provide faster detection but use more CPU.
         * Default is 200ms.
         *
         * @param interval polling interval (must be positive)
         * @return this builder
         * @throws NullPointerException     if the interval is null
         * @throws IllegalArgumentException if the interval is not positive
         */
        public Builder interval(Duration interval) {
            Objects.requireNonNull(interval, "interval cannot be null");
            if (interval.isNegative() || interval.isZero()) {
                throw new IllegalArgumentException("interval must be positive: " + interval);
            }
            this.interval = interval;
            return this;
        }

        /**
         * Builds the PollingDetector instance.
         *
         * @return new PollingDetector instance
         */
        public PollingDetector build() {
            return new PollingDetector(this);
        }
    }
}
