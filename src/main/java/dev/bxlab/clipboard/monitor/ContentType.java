package dev.bxlab.clipboard.monitor;

/**
 * Supported clipboard content types.
 */
public enum ContentType {

    /**
     * Any text type (plain, HTML, RTF). Specific flavor available in content metadata.
     */
    TEXT,

    /**
     * Any image type (PNG, JPEG, BMP). Returned as {@link java.awt.image.BufferedImage}.
     */
    IMAGE,

    /**
     * List of copied files. Returned as {@link java.util.List} of {@link java.io.File}.
     */
    FILES,

    /**
     * Unknown or unsupported type.
     */
    UNKNOWN
}
