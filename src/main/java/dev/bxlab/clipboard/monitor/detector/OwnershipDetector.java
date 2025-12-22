package dev.bxlab.clipboard.monitor.detector;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.internal.ClipboardAccessor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Detects clipboard changes using the clipboard ownership mechanism.
 * <p>
 * This detector takes ownership of the clipboard and gets notified when
 * another process takes ownership. It provides lower latency than
 * {@link PollingDetector} but may be less reliable on some platforms.
 * <p>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Detector takes clipboard ownership</li>
 *   <li>When another app copies something, the detector loses ownership</li>
 *   <li>The {@link ClipboardOwner#lostOwnership} callback fires</li>
 *   <li>After a short delay, the detector reads the new content and retakes ownership</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // With defaults (50ms delay)
 * OwnershipDetector detector = OwnershipDetector.defaults();
 *
 * // With custom delay
 * OwnershipDetector detector = OwnershipDetector.builder()
 *     .delay(Duration.ofMillis(100))
 *     .build();
 * }</pre>
 *
 * @see ChangeDetector
 * @see PollingDetector
 */
@Slf4j
public final class OwnershipDetector implements ChangeDetector, ClipboardOwner {

    /**
     * Default delay before processing clipboard change after losing ownership.
     */
    public static final Duration DEFAULT_DELAY = Duration.ofMillis(50);

    private final Duration delay;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Consumer<ClipboardContent>> contentChangeCallback = new AtomicReference<>();

    private Clipboard clipboard;

    private OwnershipDetector(Builder builder) {
        this.delay = builder.delay;
    }

    /**
     * Creates a new builder for configuring an OwnershipDetector.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an OwnershipDetector with default settings.
     *
     * @return new OwnershipDetector with defaults
     */
    public static OwnershipDetector defaults() {
        return builder().build();
    }

    /**
     * Returns the configured delay before processing changes.
     *
     * @return processing delay
     */
    @SuppressWarnings("")
    public Duration getDelay() {
        return delay;
    }

    @Override
    public void start(Consumer<ClipboardContent> callback, String initialHash) {
        if (running.getAndSet(true)) {
            log.debug("OwnershipDetector already running");
            return;
        }

        this.contentChangeCallback.set(Objects.requireNonNull(callback, "callback cannot be null"));

        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        takeOwnership();
        log.debug("OwnershipDetector started with delay: {}ms", delay.toMillis());
    }

    @Override
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        log.debug("OwnershipDetector stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Called by the system when another process takes clipboard ownership.
     *
     * @param clipboard the clipboard that lost ownership
     * @param contents  the contents that were on the clipboard
     */
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        if (!running.get()) {
            log.debug("Lost ownership but detector is stopped, ignoring");
            return;
        }

        log.debug("Lost clipboard ownership, scheduling change processing");

        Thread.ofVirtual()
                .name("clipboard-ownership-handler")
                .start(this::processOwnershipLost);
    }

    /**
     * Re-takes ownership after writing content.
     *
     * @param content content just written to clipboard
     */
    public void retakeOwnership(Transferable content) {
        if (!running.get() || content == null) {
            return;
        }

        try {
            clipboard.setContents(content, this);
            log.debug("Re-took clipboard ownership after write");
        } catch (Exception e) {
            log.warn("Could not re-take clipboard ownership: {}", e.getMessage());
        }
    }

    private void processOwnershipLost() {
        if (!running.get()) {
            return;
        }

        try {
            Thread.sleep(delay.toMillis());

            if (!running.get()) {
                return;
            }

            ClipboardContent content = ClipboardAccessor.readContent();
            contentChangeCallback.get().accept(content);

            takeOwnership();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Ownership handler interrupted");
        } catch (Exception e) {
            log.error("Error processing clipboard change after lost ownership", e);
        }
    }

    private void takeOwnership() {
        try {
            Transferable current = ClipboardAccessor.getContents();
            if (current != null) {
                clipboard.setContents(current, this);
                log.debug("Took clipboard ownership");
            } else {
                log.debug("Clipboard is empty, cannot take ownership");
            }
        } catch (IllegalStateException e) {
            log.warn("Could not take clipboard ownership: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error taking clipboard ownership", e);
        }
    }

    /**
     * Builder for creating OwnershipDetector instances.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {
        private Duration delay = DEFAULT_DELAY;

        /**
         * Sets the delay before processing clipboard change after losing ownership.
         *
         * @param delay processing delay (must be non-negative)
         * @return this builder
         * @throws NullPointerException     if delay is null
         * @throws IllegalArgumentException if delay is negative
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Builds the OwnershipDetector instance.
         *
         * @return new OwnershipDetector instance
         */
        public OwnershipDetector build() {
            Objects.requireNonNull(delay, "delay cannot be null");
            if (delay.isNegative()) {
                throw new IllegalArgumentException("delay cannot be negative: " + delay);
            }
            return new OwnershipDetector(this);
        }
    }
}
