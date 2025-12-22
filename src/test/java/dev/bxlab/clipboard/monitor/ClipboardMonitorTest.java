package dev.bxlab.clipboard.monitor;

import dev.bxlab.clipboard.monitor.detector.PollingDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ClipboardMonitorTest {

    @Nested
    class BuilderValidationTests {

        @Test
        void shouldRequireDetector() {
            ClipboardMonitor.Builder builder = ClipboardMonitor.builder()
                    .listener(content -> {
                    });

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("detector");
        }

        @Test
        void shouldRequireAtLeastOneListener() {
            ClipboardMonitor.Builder builder = ClipboardMonitor.builder()
                    .detector(PollingDetector.defaults());

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("listener");
        }

        @Test
        void shouldRejectNegativeDebounce() {
            ClipboardMonitor.Builder builder = ClipboardMonitor.builder()
                    .detector(PollingDetector.defaults())
                    .listener(content -> {
                    })
                    .debounce(Duration.ofMillis(-1));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        void shouldBuildWithValidConfiguration() {
            ClipboardMonitor monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.builder()
                            .interval(Duration.ofMillis(300))
                            .build())
                    .listener(content -> {
                    })
                    .debounce(Duration.ofMillis(150))
                    .notifyOnStart(true)
                    .build();

            assertThat(monitor).isNotNull();
            monitor.close();
        }
    }

    @Nested
    class LifecycleTests {

        private ClipboardMonitor monitor;

        @AfterEach
        void tearDown() {
            if (monitor != null) {
                monitor.close();
            }
        }

        @Test
        void shouldStartAndStop() {
            monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.defaults())
                    .listener(content -> {
                    })
                    .build();

            assertThat(monitor.isRunning()).isFalse();

            monitor.start();
            assertThat(monitor.isRunning()).isTrue();

            monitor.close();
            assertThat(monitor.isRunning()).isFalse();
        }

        @Test
        void shouldBeIdempotentOnMultipleStarts() {
            monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.defaults())
                    .listener(content -> {
                    })
                    .build();

            monitor.start();
            monitor.start();
            monitor.start();

            assertThat(monitor.isRunning()).isTrue();
        }

        @Test
        void shouldBeIdempotentOnMultipleCloses() {
            monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.defaults())
                    .listener(content -> {
                    })
                    .build();

            monitor.start();
            monitor.close();
            monitor.close();
            monitor.close();

            assertThat(monitor.isRunning()).isFalse();
        }

        @Test
        void shouldThrowOnStartAfterClose() {
            monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.defaults())
                    .listener(content -> {
                    })
                    .build();

            monitor.start();
            monitor.close();

            assertThatThrownBy(monitor::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Nested
    class IntegrationTests {

        private ClipboardMonitor monitor;
        private Clipboard systemClipboard;

        @BeforeEach
        void setUp() {
            assumeFalse(GraphicsEnvironment.isHeadless(),
                    "Skipping test in headless environment");

            try {
                systemClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            } catch (Exception e) {
                assumeTrue(false, "Clipboard not available: " + e.getMessage());
            }
        }

        @AfterEach
        void tearDown() {
            if (monitor != null) {
                monitor.close();
            }
        }

        @Test
        void shouldDetectTextChange() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ClipboardContent> received = new AtomicReference<>();

            monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.builder()
                            .interval(Duration.ofMillis(100))
                            .build())
                    .listener(content -> {
                        received.set(content);
                        latch.countDown();
                    })
                    .debounce(Duration.ofMillis(50))
                    .build();

            monitor.start();
            await().atMost(1, TimeUnit.SECONDS).until(monitor::isRunning);

            String testText = "Test clipboard content " + System.currentTimeMillis();
            systemClipboard.setContents(new StringSelection(testText), null);

            boolean notified = latch.await(3, TimeUnit.SECONDS);

            assertThat(notified).isTrue();
            assertThat(received.get()).isNotNull();
            assertThat(received.get().type()).isEqualTo(ContentType.TEXT);
            assertThat(received.get().asText()).contains(testText);
        }

        @Test
        void shouldNotNotifyForOwnContent() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);

            monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.builder()
                            .interval(Duration.ofMillis(100))
                            .build())
                    .listener(content -> latch.countDown())
                    .debounce(Duration.ofMillis(50))
                    .build();

            monitor.start();
            await().atMost(1, TimeUnit.SECONDS).until(monitor::isRunning);

            monitor.write("Content set by monitor");

            boolean notified = latch.await(1, TimeUnit.SECONDS);

            assertThat(notified).isFalse();
        }

        @Test
        void shouldReadCurrentContent() {
            monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.defaults())
                    .listener(content -> {
                    })
                    .build();

            String testText = "Current content test " + System.currentTimeMillis();
            systemClipboard.setContents(new StringSelection(testText), null);

            var content = monitor.tryRead();

            assertThat(content).isPresent();
            assertThat(content.get().type()).isEqualTo(ContentType.TEXT);
            assertThat(content.get().asText()).contains(testText);
        }

        @Test
        void shouldSupportMultipleListeners() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);

            monitor = ClipboardMonitor.builder()
                    .detector(PollingDetector.builder()
                            .interval(Duration.ofMillis(100))
                            .build())
                    .listener(content -> latch.countDown())
                    .listener(content -> latch.countDown())
                    .debounce(Duration.ofMillis(50))
                    .build();

            monitor.start();
            await().atMost(1, TimeUnit.SECONDS).until(monitor::isRunning);

            String testText = "Multi-listener test " + System.currentTimeMillis();
            systemClipboard.setContents(new StringSelection(testText), null);

            boolean completed = latch.await(3, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
        }
    }
}
