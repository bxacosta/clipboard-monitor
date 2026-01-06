package dev.bxlab.clipboard.monitor.detector;

import dev.bxlab.clipboard.monitor.content.ClipboardContent;

import java.util.function.Consumer;

/**
 * Strategy interface for clipboard change detection.
 * <p>
 * Sealed interface with two implementations:
 * {@link PollingDetector} and {@link OwnershipDetector}.
 *
 * @see PollingDetector
 * @see OwnershipDetector
 */
public sealed interface ChangeDetector permits PollingDetector, OwnershipDetector {

    /**
     * Starts the detector.
     *
     * @param callback    callback to invoke when clipboard content changes
     * @param initialHash hash of current clipboard content
     * @throws NullPointerException if callback is null
     */
    void start(Consumer<ClipboardContent> callback, String initialHash);

    /**
     * Stops the detector.
     */
    void stop();

    /**
     * Returns whether the detector is currently running.
     *
     * @return true if detector is running, false otherwise
     */
    boolean isRunning();
}
