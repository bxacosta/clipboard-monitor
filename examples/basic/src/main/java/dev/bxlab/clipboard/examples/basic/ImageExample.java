package dev.bxlab.clipboard.examples.basic;

import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.ContentType;

import java.time.Duration;

/**
 * Basic example: Monitor clipboard for image content.
 * <p>
 * Run with: {@code ./gradlew :examples:basic:runImageExample}
 * <p>
 * Copy any image to the clipboard (e.g., screenshot, image from browser),
 * and this example will detect it.
 */
@SuppressWarnings("java:S106")
public final class ImageExample {

    public static void main(String[] args) {
        System.out.println("Image Clipboard Monitor");
        System.out.println("Copy an image to see it detected. Press Ctrl+C to stop.");
        System.out.println();

        try (ClipboardMonitor monitor = ClipboardMonitor.builder()
                .pollingInterval(Duration.ofMillis(500))
                .listener(content -> {
                    if (content.getType() == ContentType.IMAGE) {
                        content.asImage().ifPresent(img -> System.out.printf("[IMAGE] %dx%d pixels, %d bytes%n",
                                img.getWidth(),
                                img.getHeight(),
                                content.getSize()));
                    } else {
                        System.out.printf("[%s] (not an image)%n", content.getType());
                    }
                })
                .build()) {

            monitor.start();

            // Run for 60 seconds or until interrupted
            Thread.sleep(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Done.");
    }
}
