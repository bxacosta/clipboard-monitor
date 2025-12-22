package dev.bxlab.clipboard.monitor.detector;

import dev.bxlab.clipboard.monitor.ClipboardContent;

import java.util.function.Consumer;

/**
 * Strategy interface for clipboard change detection.
 * <p>
 * This is a sealed interface with two permitted implementations:
 * <ul>
 *   <li>{@link PollingDetector} - Detects changes via periodic polling (recommended)</li>
 *   <li>{@link OwnershipDetector} - Detects changes via clipboard ownership (lower latency)</li>
 * </ul>
 *
 * @see PollingDetector
 * @see OwnershipDetector
 */
public sealed interface ChangeDetector permits PollingDetector, OwnershipDetector {

    /**
     * Starts the detector with the given callback and initial content hash.
     * <p>
     * The caller provides the initial hash to avoid redundant clipboard reads.
     * Safe to call multiple times (no-op if already running).
     *
     * @param callback    callback to invoke when clipboard content changes
     * @param initialHash hash of current clipboard content for change detection baseline
     * @throws NullPointerException if callback is null
     */
    void start(Consumer<ClipboardContent> callback, String initialHash);

    /**
     * Stops the detector.
     * <p>
     * After stopping, no more change notifications will be delivered.
     * Safe to call multiple times.
     */
    void stop();

    /**
     * Returns whether the detector is currently running.
     *
     * @return true if detector is running, false otherwise
     */
    boolean isRunning();
}
