package dev.bxlab.clipboard.monitor.detector;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PollingDetectorTest {

    @Test
    void shouldUseDefaultInterval() {
        PollingDetector detector = PollingDetector.defaults();

        assertThat(detector.getInterval()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void shouldBuildWithCustomInterval() {
        PollingDetector detector = PollingDetector.builder()
                .interval(Duration.ofMillis(100))
                .build();

        assertThat(detector.getInterval()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void shouldRejectZeroInterval() {
        PollingDetector.Builder builder = PollingDetector.builder().interval(Duration.ZERO);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldRejectNegativeInterval() {
        PollingDetector.Builder builder = PollingDetector.builder().interval(Duration.ofMillis(-100));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldRejectNullInterval() {
        PollingDetector.Builder builder = PollingDetector.builder().interval(null);

        assertThatThrownBy(builder::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("interval");
    }

    @Test
    void shouldStartAndStop() {
        PollingDetector detector = PollingDetector.defaults();

        assertThat(detector.isRunning()).isFalse();

        detector.start(content -> {
        }, "initial-hash");
        assertThat(detector.isRunning()).isTrue();

        detector.stop();
        assertThat(detector.isRunning()).isFalse();
    }

    @Test
    void shouldBeIdempotentOnMultipleStarts() {
        PollingDetector detector = PollingDetector.defaults();
        AtomicInteger callCount = new AtomicInteger(0);

        detector.start(content -> callCount.incrementAndGet(), "hash");
        detector.start(content -> callCount.incrementAndGet(), "hash");
        detector.start(content -> callCount.incrementAndGet(), "hash");

        assertThat(detector.isRunning()).isTrue();

        detector.stop();
    }

    @Test
    void shouldBeIdempotentOnMultipleStops() {
        PollingDetector detector = PollingDetector.defaults();

        detector.start(content -> {
        }, "hash");
        detector.stop();
        detector.stop();
        detector.stop();

        assertThat(detector.isRunning()).isFalse();
    }

    @Test
    void shouldRejectNullCallback() {
        PollingDetector detector = PollingDetector.defaults();

        assertThatThrownBy(() -> detector.start(null, "hash"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("callback");
    }

    @Test
    void shouldUpdateLastHash() {
        PollingDetector detector = PollingDetector.defaults();

        detector.start(content -> {
        }, "initial");
        detector.updateLastHash("new-hash");

        assertThat(detector.isRunning()).isTrue();
        detector.stop();
    }

    @Test
    void shouldHandleNullHashInUpdate() {
        PollingDetector detector = PollingDetector.defaults();

        detector.start(content -> {
        }, "initial");
        detector.updateLastHash(null);

        assertThat(detector.isRunning()).isTrue();
        detector.stop();
    }
}
