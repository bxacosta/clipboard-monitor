package dev.bxlab.clipboard.examples.interactive;

import dev.bxlab.clipboard.monitor.ClipboardListener;
import dev.bxlab.clipboard.monitor.ClipboardMonitor;

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
 *   <li>View monitoring statistics</li>
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
        // We need the monitor reference for DemoConsole, but DemoConsole needs the monitor.
        // Solution: use a holder that gets populated after the console is created.
        final DemoConsole[] consoleHolder = new DemoConsole[1];

        ClipboardListener forwardingListener = new ClipboardListener() {
            @Override
            public void onClipboardChange(dev.bxlab.clipboard.monitor.ClipboardContent content) {
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
                .listener(forwardingListener)
                .build();

        console = new DemoConsole(monitor);
        consoleHolder[0] = console;

        // Set up the shutdown hook for Ctrl+C
        setupShutdownHook();

        // Print a banner and start
        console.printBanner();
        monitor.start();

        // Run an interactive loop (blocks until quit)
        console.runInteractiveLoop();

        // Clean shutdown if not already done by hook
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
