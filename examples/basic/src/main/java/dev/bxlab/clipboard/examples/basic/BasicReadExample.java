package dev.bxlab.clipboard.examples.basic;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;

/**
 * Basic example: Read current clipboard content.
 * <p>
 * Run with: {@code ./gradlew :examples:basic:runBasicRead}
 */
@SuppressWarnings("java:S106")
public final class BasicReadExample {

    public static void main(String[] args) {
        // Builder requires at least one detector + listener.
        try (ClipboardMonitor monitor = ClipboardMonitor.builder()
                .detector(PollingDetector.defaults())
                .listener(content -> {
                })
                .build()) {

            var contentOpt = monitor.tryRead();
            if (contentOpt.isEmpty()) {
                System.out.println("Clipboard is empty or unavailable");
                return;
            }

            ClipboardContent content = contentOpt.get();
            System.out.println("Clipboard content:");
            System.out.println("  Type: " + content.type());
            System.out.println("  Size: " + content.size() + " bytes");
            System.out.println("  Hash: " + content.hash());

            switch (content.type()) {
                case TEXT -> content.asText().ifPresent(text -> {
                    String preview = text.length() > 100
                            ? text.substring(0, 100) + "..."
                            : text;
                    System.out.println("  Text: " + preview.replace("\n", "\\n"));
                });

                case IMAGE -> content.asImage().ifPresent(img ->
                        System.out.printf("  Image: %dx%d pixels%n", img.getWidth(), img.getHeight())
                );

                case FILES -> content.asFiles().ifPresent(files -> {
                    System.out.println("  Files:");
                    files.stream().limit(5).forEach(f ->
                            System.out.println("    - " + f.getAbsolutePath())
                    );
                    if (files.size() > 5) {
                        System.out.println("    ... and " + (files.size() - 5) + " more");
                    }
                });

                case UNKNOWN -> System.out.println("  Unknown/unsupported clipboard content");
            }
        }
    }
}
