# Clipboard Monitor

Java library for monitoring system clipboard changes in real-time. Provides reliable detection of clipboard
modifications and supports text, images, and file lists.

## Features

- Real-time clipboard change detection using a hybrid approach (ownership-based and polling)
- Support for multiple content types: text, images, and file lists
- Thread-safe implementation with configurable listeners
- Anti-loop protection for bidirectional synchronization scenarios
- Configurable polling intervals and content size limits
- Comprehensive statistics and monitoring capabilities

## Requirements

- Java 17 or higher
- SLF4J API for logging

## Installation

### Gradle

```gradle
dependencies {
    implementation 'dev.bxlab.clipboard:clipboard-monitor:1.0.0-SNAPSHOT'
}
```

### Maven

```xml
<dependency>
    <groupId>dev.bxlab.clipboard</groupId>
    <artifactId>clipboard-monitor</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Monitoring

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(content -> {
        System.out.println("Clipboard changed: " + content.getType());
    })
    .build();

monitor.start();

// Later, when done
monitor.stop();
```

### Processing Different Content Types

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(content -> {
        switch (content.getType()) {
            case TEXT -> content.asText().ifPresent(text -> 
                System.out.println("Text: " + text)
            );
            case IMAGE -> content.asImage().ifPresent(image -> 
                System.out.println("Image: " + image.getWidth() + "x" + image.getHeight())
            );
            case FILE_LIST -> content.asFileList().ifPresent(files -> 
                System.out.println("Files: " + files.size())
            );
            case UNKNOWN -> System.out.println("Unknown content type");
        }
    })
    .build();

monitor.start();
```

### Advanced Configuration

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(this::handleClipboardChange)
    .pollingInterval(Duration.ofMillis(500))
    .maxContentSize(50_000_000)
    .debounceMs(100)
    .ownershipEnabled(true)
    .notifyInitialContent(false)
    .build();

monitor.start();
```

### Bidirectional Synchronization

The library includes anti-loop protection for scenarios where clipboard content is synchronized between multiple
sources:

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(content -> synchronizeToRemote(content))
    .build();

monitor.start();

// Set content from remote source without triggering listener
monitor.setContent("content from remote");
```

## API Reference

### ClipboardMonitor

Main class for monitoring clipboard changes.

#### Builder Methods

- `listener(ClipboardListener)` - Adds a listener for clipboard changes
- `pollingInterval(Duration)` - Sets the polling interval (default: 500ms)
- `maxContentSize(long)` - Sets maximum content size in bytes (default: 100MB)
- `debounceMs(long)` - Sets debounce delay in milliseconds (default: 100ms)
- `ownershipEnabled(boolean)` - Enables/disables ownership detection (default: true)
- `notifyInitialContent(boolean)` - Notifies current clipboard content on start (default: false)

#### Methods

- `start()` - Starts monitoring clipboard changes
- `stop()` - Stops monitoring and releases resources
- `isRunning()` - Returns whether the monitor is currently active
- `setContent(String)` - Sets text content in clipboard
- `setContent(BufferedImage)` - Sets image content in clipboard
- `setContent(List<File>)` - Sets file list content in clipboard
- `getStats()` - Returns monitoring statistics

### ClipboardContent

Immutable representation of clipboard content.

#### Methods

- `getType()` - Returns the content type (TEXT, IMAGE, FILE_LIST, UNKNOWN)
- `asText()` - Returns content as text (Optional)
- `asImage()` - Returns content as BufferedImage (Optional)
- `asFileList()` - Returns content as file list (Optional)
- `getTimestamp()` - Returns capture timestamp
- `getHash()` - Returns content hash for change detection
- `getFlavors()` - Returns available data flavors

## Architecture

The library uses a hybrid detection approach:

1. **Ownership-based detection**: Fast notification when the application loses clipboard ownership
2. **Polling fallback**: Reliable detection when ownership mechanism is unavailable or bypassed
3. **Debouncing**: Prevents duplicate notifications for rapid changes
4. **Anti-loop protection**: Prevents infinite loops in synchronization scenarios

## Thread Safety

All public APIs are thread-safe. Listeners are invoked in a dedicated callback thread, ensuring that listener execution
does not block clipboard monitoring.

## License

This project is licensed under the [MIT License](LICENSE).