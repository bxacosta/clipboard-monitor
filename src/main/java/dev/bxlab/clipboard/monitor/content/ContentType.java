package dev.bxlab.clipboard.monitor.content;

/**
 * Supported clipboard content types.
 */
public enum ContentType {

    /**
     * Text content (plain, HTML, RTF).
     */
    TEXT,

    /**
     * Image content (PNG, JPEG, BMP).
     */
    IMAGE,

    /**
     * File list content.
     */
    FILES,

    /**
     * Unknown or unsupported content.
     */
    UNKNOWN
}
