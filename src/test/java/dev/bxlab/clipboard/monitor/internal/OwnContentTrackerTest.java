package dev.bxlab.clipboard.monitor.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OwnContentTrackerTest {

    private OwnContentTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = OwnContentTracker.create();
    }

    @Nested
    class TrackingTests {

        @Test
        void shouldRecognizeMarkedHash() {
            tracker.markOwn("testhash123");

            assertThat(tracker.isOwn("testhash123")).isTrue();
        }

        @Test
        void shouldNotRecognizeUnmarkedHash() {
            tracker.markOwn("hash1");

            assertThat(tracker.isOwn("unknownhash")).isFalse();
        }

        @Test
        void shouldTrackMultipleHashes() {
            tracker.markOwn("hash1");
            tracker.markOwn("hash2");
            tracker.markOwn("hash3");

            assertThat(tracker.size()).isEqualTo(3);
            assertThat(tracker.isOwn("hash1")).isTrue();
            assertThat(tracker.isOwn("hash2")).isTrue();
            assertThat(tracker.isOwn("hash3")).isTrue();
        }

        @Test
        void shouldUpdateTimestampOnRepeatedMark() {
            tracker.markOwn("hash1");

            long firstMarkTime = System.nanoTime();

            await().pollDelay(Duration.ofMillis(50))
                    .atMost(Duration.ofMillis(200))
                    .untilAsserted(() -> {
                        long elapsed = System.nanoTime() - firstMarkTime;
                        assertThat(elapsed).isGreaterThan(50_000_000L); // 50ms in nanos
                    });

            tracker.markOwn("hash1");

            assertThat(tracker.isOwn("hash1")).isTrue();
            assertThat(tracker.size()).isEqualTo(1);
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void shouldHandleNullHash() {
            tracker.markOwn(null);

            assertThat(tracker.isOwn(null)).isFalse();
        }

        @Test
        void shouldHandleEmptyHash() {
            tracker.markOwn("");

            assertThat(tracker.isOwn("")).isFalse();
        }
    }

    @Nested
    class CapacityTests {

        @Test
        void shouldEvictOldestWhenCapacityExceeded() {
            for (int i = 0; i < 11; i++) {
                tracker.markOwn("hash" + i);
            }

            assertThat(tracker.size()).isLessThanOrEqualTo(10);
        }
    }

    @Nested
    class ClearTests {

        @Test
        void shouldClearAllHashes() {
            tracker.markOwn("hash1");
            tracker.markOwn("hash2");
            tracker.markOwn("hash3");

            assertThat(tracker.size()).isEqualTo(3);

            tracker.clear();

            assertThat(tracker.size()).isZero();
            assertThat(tracker.isOwn("hash1")).isFalse();
            assertThat(tracker.isOwn("hash2")).isFalse();
            assertThat(tracker.isOwn("hash3")).isFalse();
        }
    }

    @Nested
    class ConcurrencyTests {

        @Test
        void shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 10;
            int operationsPerThread = 100;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String hash = "thread" + threadIndex + "_hash" + j;
                        tracker.markOwn(hash);
                        tracker.isOwn(hash);
                    }
                });
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }

            assertThat(tracker.size()).isLessThanOrEqualTo(10);
        }
    }
}
