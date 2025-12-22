package dev.bxlab.clipboard.monitor.internal;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OwnContentTrackerTtlTest {

    @Test
    void shouldExpireHashAfterTtl() {
        OwnContentTracker tracker = OwnContentTracker.create();
        String hash = "expiringHash";

        tracker.markOwn(hash);
        assertThat(tracker.isOwn(hash)).isTrue();

        await().atMost(7, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> !tracker.isOwn(hash));
    }
}
