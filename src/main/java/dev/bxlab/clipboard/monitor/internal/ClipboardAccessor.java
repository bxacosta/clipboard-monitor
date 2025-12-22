package dev.bxlab.clipboard.monitor.internal;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.FilesContent;
import dev.bxlab.clipboard.monitor.ImageContent;
import dev.bxlab.clipboard.monitor.TextContent;
import dev.bxlab.clipboard.monitor.UnknownContent;
import dev.bxlab.clipboard.monitor.exception.ClipboardUnavailableException;
import dev.bxlab.clipboard.monitor.util.HashUtils;
import dev.bxlab.clipboard.monitor.util.ImageUtils;
import dev.bxlab.clipboard.monitor.util.TextUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Provides static access to the system clipboard with automatic retry logic.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClipboardAccessor {

    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 50;

    /**
     * Gets the system clipboard.
     *
     * @return system clipboard
     * @throws ClipboardUnavailableException if running in headless mode
     */
    public static Clipboard getClipboard() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (HeadlessException e) {
            throw new ClipboardUnavailableException(
                    "Clipboard not available in headless environment. " +
                            "This library requires a graphical environment.", e);
        }
    }

    /**
     * Gets the current clipboard contents with automatic retry on busy.
     *
     * @return current transferable or null if the clipboard is empty
     * @throws ClipboardUnavailableException if the clipboard cannot be accessed after retries
     */
    public static Transferable getContents() {
        return getContentsWithRetry(getClipboard());
    }

    /**
     * Reads the current clipboard content.
     *
     * @return clipboard content with hash already calculated
     */
    public static ClipboardContent readContent() {
        Transferable transferable = getContents();
        return readContent(transferable);
    }

    /**
     * Reads clipboard content from a given Transferable.
     *
     * @param transferable the transferable to read from
     * @return clipboard content with hash already calculated
     */
    public static ClipboardContent readContent(Transferable transferable) {
        Instant now = Instant.now();

        if (transferable == null) {
            return new UnknownContent(HashUtils.sha256(new byte[0]), now);
        }

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                String hash = HashUtils.sha256(bytes);
                log.debug("Read text content ({} chars, {} bytes), hash: {}", text.length(), bytes.length, TextUtils.truncate(hash));
                return new TextContent(text, hash, now, bytes.length);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                BufferedImage buffered = ImageUtils.toBufferedImage(img);
                String hash = HashUtils.sha256(buffered);
                log.debug("Read image content ({}x{}), hash: {}", buffered.getWidth(), buffered.getHeight(), TextUtils.truncate(hash));
                return ImageContent.of(buffered, hash, now);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                String hash = HashUtils.sha256(files);
                log.debug("Read files content ({} files), hash: {}", files.size(), TextUtils.truncate(hash));
                return FilesContent.of(files, hash, now);
            }

            String hash = HashUtils.sha256(Arrays.toString(transferable.getTransferDataFlavors()));
            log.debug("Read unknown content type, hash: {}", TextUtils.truncate(hash));
            return new UnknownContent(hash, now);

        } catch (UnsupportedFlavorException | IOException e) {
            log.warn("Error reading clipboard content: {}", e.getMessage());
            String hash = HashUtils.sha256(String.valueOf(System.nanoTime()));
            return new UnknownContent(hash, now);
        }
    }

    private static Transferable getContentsWithRetry(Clipboard clipboard) {
        IllegalStateException lastException = null;

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return clipboard.getContents(null);
            } catch (IllegalStateException e) {
                lastException = e;
                log.debug("Clipboard busy, retry {} of {}", i + 1, MAX_RETRIES);

                if (i < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(INITIAL_RETRY_DELAY_MS * (i + 1L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ClipboardUnavailableException(
                                "Interrupted while waiting for clipboard", ie);
                    }
                }
            }
        }

        throw new ClipboardUnavailableException("Clipboard unavailable after " + MAX_RETRIES + " retries", lastException);
    }
}
