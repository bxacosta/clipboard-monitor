package dev.bxlab.clipboard.examples.basic;

import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.internal.Stats;

import java.time.Duration;

/**
 * Basic example: Monitor clipboard and display statistics.
 * <p>
 * Run with: {@code ./gradlew :examples:basic:runStatsExample}
 * <p>
 * Copy content to the clipboard and watch the statistics update.
 */
@SuppressWarnings("java:S106")
public final class StatsExample {

    public static void main(String[] args) {
        System.out.println("Clipboard Statistics Monitor");
        System.out.println("Copy content to see statistics. Press Ctrl+C to stop.");
        System.out.println();

        try (ClipboardMonitor monitor = ClipboardMonitor.builder()
                .pollingInterval(Duration.ofMillis(500))
                .listener(content -> System.out.printf("[%s] %d bytes%n", content.getType(), content.getSize()))
                .build()) {

            monitor.start();

            // Print stats every 5 seconds
            for (int i = 0; i < 12; i++) {
                Thread.sleep(5_000);
                printStats(monitor.getStats());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Done.");
    }

    private static void printStats(Stats stats) {
        System.out.println();
        System.out.println("=== Statistics ===");
        System.out.printf("  Total changes:  %d%n", stats.totalChanges());
        System.out.printf("  Total errors:   %d%n", stats.totalErrors());
        System.out.printf("  Uptime:         %s%n", formatDuration(stats.uptime()));
        System.out.println();
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        return String.format("%dm %ds", seconds / 60, seconds % 60);
    }
}
