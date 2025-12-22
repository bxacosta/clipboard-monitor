package dev.bxlab.clipboard.examples.basic;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.ContentType;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;

import java.io.File;
import java.time.Duration;

/**
 * Basic example: Monitor clipboard for file list content.
 * <p>
 * Run with: {@code ./gradlew :examples:basic:runFileListExample}
 * <p>
 * Copy files in your file explorer (Ctrl+C on selected files),
 * and this example will detect them.
 */
@SuppressWarnings("java:S106")
public final class FileListExample {

    public static void main(String[] args) {
        System.out.println("File List Clipboard Monitor");
        System.out.println("Copy files to see them detected. Press Ctrl+C to stop.");
        System.out.println();

        try (ClipboardMonitor monitor = ClipboardMonitor.builder()
                .detector(PollingDetector.builder().interval(Duration.ofMillis(500)).build())
                .listener(FileListExample::listener)
                .build()) {

            monitor.start();

            // Run for 60 seconds or until interrupted
            Thread.sleep(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Done.");
    }

    private static void listener(ClipboardContent content) {
        if (content.type() == ContentType.FILES) {
            content.asFiles().ifPresent(files -> {
                System.out.printf("[FILES] %d file(s) copied:%n", files.size());
                for (File file : files) {
                    String type = file.isDirectory() ? "DIR " : "FILE";
                    long size = file.isFile() ? file.length() : 0;
                    System.out.printf("  [%s] %s (%d bytes)%n",
                            type, file.getName(), size);
                }
                System.out.println();
            });
        } else {
            System.out.printf("[%s] (not a file list)%n", content.type());
        }
    }
}
