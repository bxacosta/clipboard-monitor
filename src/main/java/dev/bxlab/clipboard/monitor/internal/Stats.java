package dev.bxlab.clipboard.monitor.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable snapshot of monitor statistics.
 *
 * @param totalChanges total clipboard changes detected
 * @param totalErrors  total errors occurred
 * @param startTime    when monitoring started
 * @param uptime       duration since monitoring started
 */
public record Stats(
        long totalChanges,
        long totalErrors,
        Instant startTime,
        Duration uptime
) {

    @Override
    public String toString() {
        return "Stats{" +
                "totalChanges=" + totalChanges +
                ", totalErrors=" + totalErrors +
                ", uptime=" + uptime +
                '}';
    }
}
