package dev.bxlab.clipboard.monitor.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe collector for monitor statistics.
 * Use {@link #snapshot()} to get an immutable view of current statistics.
 */
public final class StatsCollector {

    private final AtomicLong totalChanges = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final Instant startTime;

    /**
     * Creates a new stats collector.
     */
    public StatsCollector() {
        this.startTime = Instant.now();
    }

    /**
     * Records a clipboard change.
     */
    public void recordChange() {
        totalChanges.incrementAndGet();
    }

    /**
     * Records an error.
     */
    public void recordError() {
        totalErrors.incrementAndGet();
    }

    /**
     * Creates an immutable snapshot of current statistics.
     *
     * @return current statistics
     */
    public Stats snapshot() {
        return new Stats(
                totalChanges.get(),
                totalErrors.get(),
                startTime,
                Duration.between(startTime, Instant.now())
        );
    }
}
