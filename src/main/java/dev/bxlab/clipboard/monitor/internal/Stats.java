package dev.bxlab.clipboard.monitor.internal;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Basic clipboard monitor statistics. Thread-safe using AtomicLong counters.
 */
@Getter
public final class Stats {

    private final AtomicLong totalChanges = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final Instant startTime;

    public Stats() {
        this.startTime = Instant.now();
    }

    public void recordChange() {
        totalChanges.incrementAndGet();
    }

    public void recordError() {
        totalErrors.incrementAndGet();
    }

    public long getTotalChanges() {
        return totalChanges.get();
    }

    public long getTotalErrors() {
        return totalErrors.get();
    }

    public Duration getUptime() {
        return Duration.between(startTime, Instant.now());
    }

    @Override
    public String toString() {
        return "Stats{" +
                "totalChanges=" + totalChanges.get() +
                ", totalErrors=" + totalErrors.get() +
                ", uptime=" + getUptime() +
                '}';
    }
}
