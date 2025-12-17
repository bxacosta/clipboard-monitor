package dev.bxlab.clipboard.monitor.internal;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StatsCollectorTest {

    @Test
    void shouldStartWithZeroCounters() {
        StatsCollector collector = new StatsCollector();
        Stats stats = collector.snapshot();

        assertThat(stats.totalChanges()).isZero();
        assertThat(stats.totalErrors()).isZero();
    }

    @Test
    void shouldRecordChanges() {
        StatsCollector collector = new StatsCollector();

        collector.recordChange();
        collector.recordChange();
        collector.recordChange();

        Stats stats = collector.snapshot();
        assertThat(stats.totalChanges()).isEqualTo(3);
    }

    @Test
    void shouldRecordErrors() {
        StatsCollector collector = new StatsCollector();

        collector.recordError();
        collector.recordError();

        Stats stats = collector.snapshot();
        assertThat(stats.totalErrors()).isEqualTo(2);
    }

    @Test
    void shouldTrackUptime() {
        StatsCollector collector = new StatsCollector();

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> true);

        Stats stats = collector.snapshot();
        assertThat(stats.uptime().toMillis()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void shouldHaveStartTime() {
        StatsCollector collector = new StatsCollector();
        Stats stats = collector.snapshot();

        assertThat(stats.startTime()).isNotNull();
        assertThat(stats.startTime()).isBeforeOrEqualTo(java.time.Instant.now());
    }

    @Test
    void shouldHaveReadableToString() {
        StatsCollector collector = new StatsCollector();
        collector.recordChange();
        collector.recordError();

        Stats stats = collector.snapshot();
        String str = stats.toString();

        assertThat(str)
                .contains("totalChanges=1")
                .contains("totalErrors=1")
                .contains("uptime=");
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        StatsCollector collector = new StatsCollector();
        int threadCount = 10;
        int incrementsPerThread = 1000;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    collector.recordChange();
                    collector.recordError();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        Stats stats = collector.snapshot();
        assertThat(stats.totalChanges()).isEqualTo(threadCount * incrementsPerThread);
        assertThat(stats.totalErrors()).isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    void snapshotsShouldBeImmutable() {
        StatsCollector collector = new StatsCollector();
        collector.recordChange();

        Stats snapshot1 = collector.snapshot();

        collector.recordChange();
        collector.recordChange();

        Stats snapshot2 = collector.snapshot();

        assertThat(snapshot1.totalChanges()).isEqualTo(1);
        assertThat(snapshot2.totalChanges()).isEqualTo(3);
    }
}
