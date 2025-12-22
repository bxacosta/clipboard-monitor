package dev.bxlab.clipboard.monitor.detector;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnershipDetectorTest {

    @Test
    void shouldUseDefaultDelay() {
        OwnershipDetector detector = OwnershipDetector.defaults();

        assertThat(detector.getDelay()).isEqualTo(Duration.ofMillis(50));
    }

    @Test
    void shouldBuildWithCustomDelay() {
        OwnershipDetector detector = OwnershipDetector.builder()
                .delay(Duration.ofMillis(100))
                .build();

        assertThat(detector.getDelay()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void shouldRejectNegativeDelay() {
        OwnershipDetector.Builder builder = OwnershipDetector.builder().delay(Duration.ofMillis(-1));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void shouldRejectNullDelay() {
        OwnershipDetector.Builder builder = OwnershipDetector.builder().delay(null);

        assertThatThrownBy(builder::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("delay");
    }

    @Test
    void shouldAcceptZeroDelay() {
        OwnershipDetector detector = OwnershipDetector.builder()
                .delay(Duration.ZERO)
                .build();

        assertThat(detector.getDelay()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldStartAndStop() {
        OwnershipDetector detector = OwnershipDetector.defaults();

        assertThat(detector.isRunning()).isFalse();

        detector.start(content -> {
        }, "initial-hash");
        assertThat(detector.isRunning()).isTrue();

        detector.stop();
        assertThat(detector.isRunning()).isFalse();
    }

    @Test
    void shouldBeIdempotentOnMultipleStarts() {
        OwnershipDetector detector = OwnershipDetector.defaults();
        AtomicInteger callCount = new AtomicInteger(0);

        detector.start(content -> callCount.incrementAndGet(), "hash");
        detector.start(content -> callCount.incrementAndGet(), "hash");
        detector.start(content -> callCount.incrementAndGet(), "hash");

        assertThat(detector.isRunning()).isTrue();

        detector.stop();
    }

    @Test
    void shouldBeIdempotentOnMultipleStops() {
        OwnershipDetector detector = OwnershipDetector.defaults();

        detector.start(content -> {
        }, "hash");
        detector.stop();
        detector.stop();
        detector.stop();

        assertThat(detector.isRunning()).isFalse();
    }

    @Test
    void shouldRejectNullCallback() {
        OwnershipDetector detector = OwnershipDetector.defaults();

        assertThatThrownBy(() -> detector.start(null, "hash"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("callback");
    }

    @Test
    void shouldRetakeOwnershipWithNullContent() {
        OwnershipDetector detector = OwnershipDetector.defaults();

        detector.start(content -> {
        }, "hash");
        detector.retakeOwnership(null);

        assertThat(detector.isRunning()).isTrue();
        detector.stop();
    }

    @Test
    void shouldIgnoreRetakeOwnershipWhenStopped() {
        OwnershipDetector detector = OwnershipDetector.defaults();

        detector.retakeOwnership(null);

        assertThat(detector.isRunning()).isFalse();
    }
}
