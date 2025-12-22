package dev.bxlab.clipboard.examples.interactive;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardListener;
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;

/**
 * Interactive demo for ClipboardMonitor.
 * <p>
 * Run with: {@code ./gradlew :examples:interactive:run}
 * <p>
 * Features:
 * <ul>
 *   <li>Real-time clipboard change notifications</li>
 *   <li>Copy text, images, or files to the clipboard</li>
 *   <li>Read current clipboard content</li>
 * </ul>
 */
public final class InteractiveDemo {

    private ClipboardMonitor monitor;
    private DemoConsole console;
    private volatile boolean shuttingDown = false;

    /**
     * Entry point for the demo application.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        new InteractiveDemo().run();
    }

    private void run() {
        final DemoConsole[] consoleHolder = new DemoConsole[1];

        ClipboardListener forwardingListener = new ClipboardListener() {
            @Override
            public void onClipboardChange(ClipboardContent content) {
                if (consoleHolder[0] != null) {
                    consoleHolder[0].printClipboardChange(content);
                }
            }

            @Override
            public void onError(Exception error) {
                if (consoleHolder[0] != null) {
                    consoleHolder[0].printError(error);
                }
            }
        };

        monitor = ClipboardMonitor.builder()
                .detector(PollingDetector.defaults())
                .listener(forwardingListener)
                .build();

        console = new DemoConsole(monitor);
        consoleHolder[0] = console;

        setupShutdownHook();

        console.printBanner();
        monitor.start();

        console.runInteractiveLoop();

        if (!shuttingDown) {
            shutdown();
        }
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!shuttingDown) {
                shutdown();
            }
        }, "demo-shutdown-hook"));
    }

    private synchronized void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;

        console.stopLoop();
        console.printShutdown();
        monitor.close();
    }
}
