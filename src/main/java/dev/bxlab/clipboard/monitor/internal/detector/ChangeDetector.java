package dev.bxlab.clipboard.monitor.internal.detector;

/**
 * Interface for clipboard change detection strategies.
 */
public interface ChangeDetector {

    /**
     * Starts the detector.
     * Safe to call multiple times (no-op if already running).
     */
    void start();

    /**
     * Stops the detector.
     * Safe to call multiple times.
     */
    void stop();

    /**
     * @return true if detector is currently running
     */
    boolean isRunning();
}
