package dev.bxlab.clipboard.monitor;

import java.time.Duration;

/**
 * Immutable snapshot of clipboard monitor statistics.
 * <p>
 * Provides basic operational metrics for monitoring the health and
 * activity of a {@link ClipboardMonitor} instance.
 *
 * <pre>{@code
 * MonitorStats stats = monitor.stats();
 * System.out.println("Changes detected: " + stats.changesDetected());
 * System.out.println("Errors: " + stats.errors());
 * System.out.println("Uptime: " + stats.uptime());
 * }</pre>
 *
 * @param changesDetected total number of clipboard changes detected and notified
 * @param errors          total number of errors that occurred during monitoring
 * @param uptime          duration since monitoring started
 */
public record MonitorStats(
        long changesDetected,
        long errors,
        Duration uptime
) {

    /**
     * Creates a new MonitorStats instance.
     *
     * @throws IllegalArgumentException if changesDetected or errors is negative
     * @throws NullPointerException     if uptime is null
     */
    public MonitorStats {
        if (changesDetected < 0) {
            throw new IllegalArgumentException("changesDetected cannot be negative: " + changesDetected);
        }
        if (errors < 0) {
            throw new IllegalArgumentException("errors cannot be negative: " + errors);
        }
        if (uptime == null) {
            throw new NullPointerException("uptime cannot be null");
        }
    }

    /**
     * Creates initial statistics with zero counts and zero uptime.
     *
     * @return initial stats instance
     */
    public static MonitorStats initial() {
        return new MonitorStats(0, 0, Duration.ZERO);
    }

    @Override
    public String toString() {
        return "MonitorStats{" +
                "changesDetected=" + changesDetected +
                ", errors=" + errors +
                ", uptime=" + formatDuration(uptime) +
                '}';
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }
}
