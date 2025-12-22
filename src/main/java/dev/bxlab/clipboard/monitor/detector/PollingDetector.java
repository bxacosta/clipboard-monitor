package dev.bxlab.clipboard.monitor.detector;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.internal.ClipboardAccessor;
import dev.bxlab.clipboard.monitor.util.TextUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final AtomicReference<Consumer<ClipboardContent>> contentChangeCallback = new AtomicReference<>();
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
    @SuppressWarnings("")
    public Duration getInterval() {
        return interval;
    }

    @Override
    public void start(Consumer<ClipboardContent> callback, String initialHash) {
        if (running.getAndSet(true)) {
            log.debug("PollingDetector already running");
            return;
        }

        this.contentChangeCallback.set(Objects.requireNonNull(callback, "callback cannot be null"));
        this.lastHash = initialHash != null ? initialHash : "";

        pollingThread.set(Thread.ofVirtual()
                .name("clipboard-polling-detector")
                .start(this::pollLoop));

        log.debug("PollingDetector started with interval: {}ms", interval.toMillis());
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

        log.debug("PollingDetector stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Updates the last known hash after writing to clipboard.
     *
     * @param hash new hash to remember
     */
    public void updateLastHash(String hash) {
        this.lastHash = hash != null ? hash : "";
        log.debug("Updated last known hash: {}", TextUtils.truncate(this.lastHash));
    }

    @SuppressWarnings("BusyWait")
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
            ClipboardContent content = ClipboardAccessor.readContent();
            String currentHash = content.hash();

            if (!currentHash.equals(lastHash)) {
                log.debug("Clipboard change detected via polling. Old: {}, New: {}",
                        TextUtils.truncate(lastHash),
                        TextUtils.truncate(currentHash));

                lastHash = currentHash;
                contentChangeCallback.get().accept(content);
            }
        } catch (IllegalStateException e) {
            log.debug("Clipboard busy during poll, will retry next cycle");
        }
    }

    /**
     * Builder for creating PollingDetector instances.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {
        private Duration interval = DEFAULT_INTERVAL;

        /**
         * Sets the polling interval.
         *
         * @param interval polling interval (must be positive)
         * @return this builder
         * @throws NullPointerException     if the interval is null
         * @throws IllegalArgumentException if the interval is not positive
         */
        public Builder interval(Duration interval) {
            this.interval = interval;
            return this;
        }

        /**
         * Builds the PollingDetector instance.
         *
         * @return new PollingDetector instance
         */
        public PollingDetector build() {
            Objects.requireNonNull(interval, "interval cannot be null");
            if (interval.isNegative() || interval.isZero()) {
                throw new IllegalArgumentException("interval must be positive: " + interval);
            }
            return new PollingDetector(this);
        }
    }
}
