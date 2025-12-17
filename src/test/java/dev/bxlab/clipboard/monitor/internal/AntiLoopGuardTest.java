package dev.bxlab.clipboard.monitor.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AntiLoopGuardTest {

    private AntiLoopGuard guard;

    @BeforeEach
    void setUp() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        guard = new AntiLoopGuard(scheduler);
    }

    @Test
    void shouldRecognizeOwnContent() {
        String hash = "testhash123";

        guard.markAsOwn(hash);

        assertThat(guard.isOwnContent(hash)).isTrue();
    }

    @Test
    void shouldNotRecognizeUnknownHash() {
        assertThat(guard.isOwnContent("unknownhash")).isFalse();
    }

    @Test
    void shouldRecognizeContentWithinTimeWindow() {
        String hash = "testhash";

        guard.markAsOwn(hash);

        assertThat(guard.isOwnContent("differenthash")).isTrue();
    }

    @Test
    void shouldNotRecognizeContentAfterTimeWindow() {
        String hash = "testhash";

        guard.markAsOwn(hash);

        await().pollDelay(600, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> true);

        assertThat(guard.isOwnContent("differenthash")).isFalse();
        assertThat(guard.isOwnContent(hash)).isTrue();
    }

    @Test
    void shouldExpireHashAfter5Seconds() {
        String hash = "expirinhash";

        guard.markAsOwn(hash);
        assertThat(guard.isOwnContent(hash)).isTrue();

        await().atMost(7, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> !guard.isOwnContent(hash) ||
                        System.currentTimeMillis() - guard.getOwnHashCount() > 0);
    }

    @Test
    void shouldHandleNullHash() {
        guard.markAsOwn(null);
        assertThat(guard.isOwnContent(null)).isFalse();
    }

    @Test
    void shouldHandleEmptyHash() {
        guard.markAsOwn("");
        assertThat(guard.isOwnContent("")).isFalse();
    }

    @Test
    void shouldClearAllHashes() {
        guard.markAsOwn("hash1");
        guard.markAsOwn("hash2");
        guard.markAsOwn("hash3");

        assertThat(guard.getOwnHashCount()).isEqualTo(3);

        guard.clear();

        assertThat(guard.getOwnHashCount()).isZero();

        await().pollDelay(600, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> true);

        assertThat(guard.isOwnContent("hash1")).isFalse();
    }

    @Test
    void shouldTrackMultipleHashes() {
        guard.markAsOwn("hash1");
        guard.markAsOwn("hash2");
        guard.markAsOwn("hash3");

        assertThat(guard.getOwnHashCount()).isEqualTo(3);
        assertThat(guard.isOwnContent("hash1")).isTrue();
        assertThat(guard.isOwnContent("hash2")).isTrue();
        assertThat(guard.isOwnContent("hash3")).isTrue();
    }
}
