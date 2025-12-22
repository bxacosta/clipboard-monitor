package dev.bxlab.clipboard.examples.basic;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardListener;
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Basic example: Using multiple clipboard listeners.
 * <p>
 * Run with: {@code ./gradlew :examples:basic:runListenerExample}
 * <p>
 * Demonstrates how to attach multiple listeners to a single monitor,
 * each handling clipboard changes differently.
 */
@SuppressWarnings("java:S106")
public final class ListenerExample {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("Multiple Listeners Example");
        System.out.println("Copy content to see multiple listeners react. Press Ctrl+C to stop.");
        System.out.println();

        ClipboardListener loggingListener = new LoggingListener("Logger");
        ClipboardListener textOnlyListener = new TextOnlyListener("TextFilter");
        ClipboardListener countingListener = new CountingListener("Counter");

        try (ClipboardMonitor monitor = ClipboardMonitor.builder()
                .detector(PollingDetector.builder().interval(Duration.ofMillis(500)).build())
                .listener(loggingListener)
                .listener(textOnlyListener)
                .listener(countingListener)
                .build()) {

            monitor.start();

            // Run for 30 seconds
            Thread.sleep(30_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Done.");
    }

    static class LoggingListener implements ClipboardListener {
        private final String name;

        LoggingListener(String name) {
            this.name = name;
        }

        @Override
        public void onClipboardChange(ClipboardContent content) {
            String time = LocalTime.now().format(TIME_FMT);
            System.out.printf("[%s][%s] %s - %d bytes%n",
                    time, name, content.type(), content.size());
        }

        @Override
        public void onError(Exception error) {
            System.out.printf("[%s] ERROR: %s%n", name, error.getMessage());
        }
    }

    static class TextOnlyListener implements ClipboardListener {
        private final String name;

        TextOnlyListener(String name) {
            this.name = name;
        }

        @Override
        public void onClipboardChange(ClipboardContent content) {
            content.asText().ifPresent(text -> {
                String preview = text.length() > 50
                        ? text.substring(0, 50) + "..."
                        : text;
                preview = preview.replace("\n", "\\n");
                System.out.printf("[%s] Text: \"%s\"%n", name, preview);
            });
        }
    }

    static class CountingListener implements ClipboardListener {
        private final String name;
        private int textCount = 0;
        private int imageCount = 0;
        private int filesCount = 0;
        private int otherCount = 0;

        CountingListener(String name) {
            this.name = name;
        }

        @Override
        public void onClipboardChange(ClipboardContent content) {
            switch (content.type()) {
                case TEXT -> textCount++;
                case IMAGE -> imageCount++;
                case FILES -> filesCount++;
                case UNKNOWN -> otherCount++;
            }
            System.out.printf("[%s] Counts: text=%d, image=%d, files=%d, other=%d%n",
                    name, textCount, imageCount, filesCount, otherCount);
        }
    }
}
