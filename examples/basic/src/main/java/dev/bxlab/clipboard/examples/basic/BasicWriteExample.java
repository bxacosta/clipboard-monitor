package dev.bxlab.clipboard.examples.basic;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;

/**
 * Basic example: Write text to clipboard.
 * <p>
 * Run with: {@code ./gradlew :examples:basic:runBasicWrite}
 */
@SuppressWarnings("java:S106")
public final class BasicWriteExample {

    public static void main(String[] args) {
        String textToWrite = args.length > 0
                ? String.join(" ", args)
                : "Hello from ClipboardMonitor! (timestamp: " + System.currentTimeMillis() + ")";

        try (ClipboardMonitor monitor = ClipboardMonitor.builder()
                .detector(PollingDetector.defaults())
                .listener(content -> {
                })
                .build()) {

            monitor.write(textToWrite);
            System.out.println("Text copied to clipboard:");
            System.out.println("  \"" + textToWrite + "\"");

            monitor.tryRead()
                    .flatMap(ClipboardContent::asText)
                    .ifPresent(text -> {
                        boolean matches = text.equals(textToWrite);
                        System.out.println("  Verified: " + (matches ? "OK" : "MISMATCH"));
                    });
        }
    }
}
