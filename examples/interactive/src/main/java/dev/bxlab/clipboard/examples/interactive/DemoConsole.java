package dev.bxlab.clipboard.examples.interactive;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardMonitor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/**
 * Handles user input/output for the demo application.
 */

public final class DemoConsole {

    private final Scanner scanner;
    private final PrintStream out;
    private final ClipboardMonitor monitor;
    private volatile boolean running;

    /**
     * Creates a new console handler.
     *
     * @param monitor the clipboard monitor instance
     */
    public DemoConsole(ClipboardMonitor monitor) {
        this.scanner = new Scanner(System.in);
        this.out = System.out;
        this.monitor = monitor;
        this.running = true;
    }

    /**
     * Prints the welcome banner.
     */
    public void printBanner() {
        out.println();
        out.println("========================================");
        out.println("  Clipboard Monitor - Interactive Demo");
        out.println("========================================");
        out.println("Commands: 1=text 2=image 3=files 4=read h=help q=quit");
        out.println();
    }

    /**
     * Prints the help message.
     */
    public void printHelp() {
        out.println();
        out.println("Available commands:");
        out.println("  1 - Copy text to clipboard");
        out.println("  2 - Copy image to clipboard (from file path)");
        out.println("  3 - Copy file(s) to clipboard");
        out.println("  4 - Read current clipboard content");
        out.println("  h - Show this help");
        out.println("  q - Quit");
        out.println();
        out.println("For files, use semicolon to separate multiple paths:");
        out.println("  Example: C:\\file1.txt;C:\\file2.txt");
        out.println();
    }

    /**
     * Runs the interactive command loop.
     * Blocks until the user quits or the loop is stopped.
     */
    @SuppressWarnings("java:S135")
    public void runInteractiveLoop() {
        out.println("Monitoring started. Clipboard changes will appear below.");
        out.println();

        while (running) {
            out.print("> ");
            out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim().toLowerCase();
            if (input.isEmpty()) {
                continue;
            }

            processCommand(input);
        }
    }

    /**
     * Stops the interactive loop.
     */
    public void stopLoop() {
        running = false;
    }

    /**
     * Prints a clipboard change notification.
     * Thread-safe for use from listener callback.
     *
     * @param content the new clipboard content
     */
    public synchronized void printClipboardChange(ClipboardContent content) {
        String timestamp = ContentHelper.formatTimestamp();
        String type = content.type().toString();
        String size = ContentHelper.formatSize(content.size());

        out.println();
        out.printf("[%s] %s (%s)%n", timestamp, type, size);
        out.println(formatContent(content));
        out.println();
        out.print("> ");
        out.flush();
    }

    /**
     * Prints an error notification.
     * Thread-safe for use from listener callback.
     *
     * @param error the exception that occurred
     */
    public synchronized void printError(Exception error) {
        out.println();
        out.println("[ERROR] " + error.getMessage());
        out.println();
        out.print("> ");
        out.flush();
    }

    /**
     * Prints shutdown message.
     */
    public void printShutdown() {
        out.println();
        out.println("Stopping...");
        out.println("Goodbye!");
    }

    private void processCommand(String command) {
        switch (command) {
            case "1" -> handleSetText();
            case "2" -> handleSetImage();
            case "3" -> handleSetFiles();
            case "4" -> handleReadCurrent();
            case "h", "help" -> printHelp();
            case "q", "quit", "exit" -> running = false;
            default -> out.println("Unknown command. Type 'h' for help.");
        }
    }

    private void handleSetText() {
        out.print("Enter text: ");
        out.flush();

        if (!scanner.hasNextLine()) {
            return;
        }

        String text = scanner.nextLine();
        if (text.isEmpty()) {
            out.println("[WARN] Empty text, nothing copied");
            return;
        }

        try {
            monitor.write(text);
            out.println("[OK] Text copied to clipboard");
        } catch (Exception e) {
            out.println("[ERROR] Failed to copy text: " + e.getMessage());
        }
    }

    private void handleSetImage() {
        out.print("Enter image path: ");
        out.flush();

        if (!scanner.hasNextLine()) {
            return;
        }

        String path = scanner.nextLine().trim();
        if (path.isEmpty()) {
            out.println("[WARN] No path provided");
            return;
        }

        path = removeQuotes(path);

        if (!ContentHelper.isImageFile(path)) {
            out.println("[ERROR] Not a supported image file (use .png, .jpg, .jpeg)");
            return;
        }

        try {
            BufferedImage image = ContentHelper.loadImage(path);
            monitor.write(image);
            out.printf("[OK] Image copied (%dx%d)%n", image.getWidth(), image.getHeight());
        } catch (IOException e) {
            out.println("[ERROR] " + e.getMessage());
        } catch (Exception e) {
            out.println("[ERROR] Failed to copy image: " + e.getMessage());
        }
    }

    private void handleSetFiles() {
        out.print("Enter file path(s): ");
        out.flush();

        if (!scanner.hasNextLine()) {
            return;
        }

        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            out.println("[WARN] No path provided");
            return;
        }

        input = removeQuotes(input);

        try {
            List<File> files = ContentHelper.parseFilePaths(input);
            monitor.write(files);
            out.printf("[OK] %d file(s) copied%n", files.size());
        } catch (IOException e) {
            out.println("[ERROR] " + e.getMessage());
        } catch (Exception e) {
            out.println("[ERROR] Failed to copy files: " + e.getMessage());
        }
    }

    private void handleReadCurrent() {
        var contentOpt = monitor.tryRead();

        if (contentOpt.isEmpty()) {
            out.println("Clipboard is empty or unavailable");
            return;
        }

        ClipboardContent content = contentOpt.get();
        String type = content.type().toString();
        String size = ContentHelper.formatSize(content.size());

        out.printf("Current: %s (%s)%n", type, size);
        out.println(formatContent(content));
    }

    private String formatContent(ClipboardContent content) {
        return switch (content.type()) {
            case TEXT -> content.asText()
                    .map(ContentHelper::truncateLines)
                    .orElse("(empty text)");

            case IMAGE -> content.asImage()
                    .map(img -> String.format("Dimensions: %dx%d", img.getWidth(), img.getHeight()))
                    .orElse("(image data unavailable)");

            case FILES -> content.asFiles()
                    .map(this::formatFileListFull)
                    .orElse("(no files)");

            case UNKNOWN -> "(unknown/unsupported clipboard content)";
        };
    }

    private String formatFileListFull(List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File file : files) {
            sb.append("  - ").append(file.getAbsolutePath()).append("\n");
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private String removeQuotes(String path) {
        if (path.length() < 2) return path;

        if ((path.startsWith("\"") && path.endsWith("\"")) ||
                (path.startsWith("'") && path.endsWith("'"))) {
            return path.substring(1, path.length() - 1);
        }
        return path;
    }
}
