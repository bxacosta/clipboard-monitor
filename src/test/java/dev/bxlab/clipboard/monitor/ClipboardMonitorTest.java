package dev.bxlab.clipboard.monitor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
            monitor = null;
        }
    }

    @Test
    void shouldBuildWithListener() {
        AtomicReference<ClipboardContent> received = new AtomicReference<>();

        monitor = ClipboardMonitor.builder()
                .listener(received::set)
                .build();

        assertThat(monitor).isNotNull();
        assertThat(monitor.isRunning()).isFalse();
    }

    @Test
    void shouldRequireAtLeastOneListener() {
        ClipboardMonitor.ClipboardMonitorBuilder builder = ClipboardMonitor.builder();

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener");
    }

    @Test
    void shouldStartAndClose() {
        monitor = ClipboardMonitor.builder()
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
    void shouldDetectTextChange() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ClipboardContent> received = new AtomicReference<>();

        monitor = ClipboardMonitor.builder()
                .listener(content -> {
                    received.set(content);
                    latch.countDown();
                })
                .pollingInterval(Duration.ofMillis(100))
                .debounce(Duration.ofMillis(50))
                .ownershipEnabled(false)
                .build();

        monitor.start();

        await().atMost(1, TimeUnit.SECONDS).until(monitor::isRunning);

        String testText = "Test clipboard content " + System.currentTimeMillis();
        systemClipboard.setContents(new StringSelection(testText), null);

        boolean notified = latch.await(3, TimeUnit.SECONDS);

        assertThat(notified).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().getType()).isEqualTo(ContentType.TEXT);
        assertThat(received.get().asText()).contains(testText);
    }

    @Test
    void shouldNotNotifyForOwnContent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        monitor = ClipboardMonitor.builder()
                .listener(content -> latch.countDown())
                .pollingInterval(Duration.ofMillis(100))
                .debounce(Duration.ofMillis(50))
                .build();

        monitor.start();

        await().atMost(1, TimeUnit.SECONDS).until(monitor::isRunning);

        monitor.setContent("Content set by monitor");

        boolean notified = latch.await(1, TimeUnit.SECONDS);

        assertThat(notified).isFalse();
    }

    @Test
    void shouldReadCurrentContent() {
        monitor = ClipboardMonitor.builder()
                .listener(content -> {
                })
                .build();

        String testText = "Current content test";
        systemClipboard.setContents(new StringSelection(testText), null);

        var content = monitor.getCurrentContent();

        assertThat(content).isPresent();
        assertThat(content.get().getType()).isEqualTo(ContentType.TEXT);
        assertThat(content.get().asText()).contains(testText);
    }

    @Test
    void shouldTrackStats() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        monitor = ClipboardMonitor.builder()
                .listener(content -> latch.countDown())
                .pollingInterval(Duration.ofMillis(100))
                .debounce(Duration.ofMillis(50))
                .ownershipEnabled(false)
                .build();

        monitor.start();

        await().atMost(1, TimeUnit.SECONDS).until(monitor::isRunning);

        systemClipboard.setContents(new StringSelection("Change 1 " + System.nanoTime()), null);

        await().pollDelay(200, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> true);

        systemClipboard.setContents(new StringSelection("Change 2 " + System.nanoTime()), null);

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        var stats = monitor.getStats();
        assertThat(stats.totalChanges()).isGreaterThanOrEqualTo(2);
        assertThat(stats.uptime().toMillis()).isGreaterThan(0);
    }

    @Test
    void shouldAcceptCustomConfiguration() {
        monitor = ClipboardMonitor.builder()
                .listener(content -> {
                })
                .pollingInterval(Duration.ofMillis(300))
                .debounce(Duration.ofMillis(150))
                .ownershipEnabled(false)
                .ignoreOwnChanges(false)
                .build();

        assertThat(monitor).isNotNull();
    }

    @Test
    void shouldRejectInvalidPollingInterval() {
        ClipboardMonitor.ClipboardMonitorBuilder builder1 = ClipboardMonitor.builder()
                .listener(content -> {
                })
                .pollingInterval(Duration.ZERO);

        assertThatThrownBy(builder1::build)
                .isInstanceOf(IllegalArgumentException.class);

        ClipboardMonitor.ClipboardMonitorBuilder builder2 = ClipboardMonitor.builder()
                .listener(content -> {
                })
                .pollingInterval(Duration.ofMillis(-100));

        assertThatThrownBy(builder2::build)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidDebounce() {
        ClipboardMonitor.ClipboardMonitorBuilder builder = ClipboardMonitor.builder()
                .listener(content -> {
                })
                .debounce(Duration.ofMillis(-1));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAddAndRemoveListeners() {
        AtomicReference<Integer> callCount = new AtomicReference<>(0);

        ClipboardListener listener1 = content -> callCount.updateAndGet(v -> v + 1);
        ClipboardListener listener2 = content -> callCount.updateAndGet(v -> v + 10);

        monitor = ClipboardMonitor.builder()
                .listener(listener1)
                .build();

        monitor.addListener(listener2);

        assertThat(monitor.removeListener(listener2)).isTrue();
        assertThat(monitor.removeListener(listener2)).isFalse();
    }

    @Test
    void shouldBeIdempotentOnMultipleStarts() {
        monitor = ClipboardMonitor.builder()
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
                .listener(content -> {
                })
                .build();

        monitor.start();
        monitor.close();
        monitor.close();
        monitor.close();

        assertThat(monitor.isRunning()).isFalse();
    }
}
