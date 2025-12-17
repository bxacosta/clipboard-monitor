package dev.bxlab.clipboard.examples.basic;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardMonitor;

/**
 * Basic example: Write text to clipboard.
 * <p>
 * Run with: {@code ./gradlew :examples:basic:runBasicWrite}
 */
@SuppressWarnings("java:S106")
public final class BasicWriteExample {

    public static void main(String[] args) {
        // Get text from args or use default
        String textToWrite = args.length > 0
                ? String.join(" ", args)
                : "Hello from ClipboardMonitor! (timestamp: " + System.currentTimeMillis() + ")";

        try (ClipboardMonitor monitor = ClipboardMonitor.builder()
                .listener(content -> {
                })
                .build()) {

            // Write text to clipboard
            monitor.setContent(textToWrite);
            System.out.println("Text copied to clipboard:");
            System.out.println("  \"" + textToWrite + "\"");

            // Verify by reading back
            monitor.getCurrentContent().flatMap(ClipboardContent::asText).ifPresent(text -> {
                boolean matches = text.equals(textToWrite);
                System.out.println("  Verified: " + (matches ? "OK" : "MISMATCH"));
            });
        }
    }
}
