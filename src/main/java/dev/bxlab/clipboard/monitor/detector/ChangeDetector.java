package dev.bxlab.clipboard.monitor.detector;

/**
 * Strategy interface for clipboard change detection.
 * <p>
 * This is a sealed interface with two permitted implementations:
 * <ul>
 *   <li>{@link PollingDetector} - Detects changes via periodic polling (recommended)</li>
 *   <li>{@link OwnershipDetector} - Detects changes via clipboard ownership (lower latency)</li>
 * </ul>
 * <p>
 *
 * @see PollingDetector
 * @see OwnershipDetector
 */
public sealed interface ChangeDetector permits PollingDetector, OwnershipDetector {

    /**
     * Starts the detector.
     * <p>
     * Must be called before the detector will begin detecting changes.
     * Safe to call multiple times (no-op if already running).
     */
    void start();

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
