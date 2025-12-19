# Clipboard Monitor - Technical Reference

## Overview

Java library for monitoring system clipboard changes. Supports text, images, and file lists.

**Package**: `dev.bxlab.clipboard.monitor`  
**Minimum Java**: 17  
**Dependencies**: SLF4J API

## Installation

```gradle
dependencies {
    implementation 'dev.bxlab.clipboard:clipboard-monitor:1.0.0-SNAPSHOT'
}
```

---

## Core Classes

### ClipboardMonitor

Main entry point. Implements `AutoCloseable`.

#### Creating an Instance

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(content -> { /* handle change */ })
    .build();
```

#### Builder Options

| Method                          | Type     | Default | Description                                         |
|---------------------------------|----------|---------|-----------------------------------------------------|
| `listener(ClipboardListener)`   | required | -       | Callback for clipboard changes. Can add multiple.   |
| `pollingInterval(Duration)`     | optional | 500ms   | Interval between clipboard polls.                   |
| `debounce(Duration)`            | optional | 100ms   | Delay to group rapid changes into one notification. |
| `ownershipEnabled(boolean)`     | optional | true    | Enable fast ownership-based detection.              |
| `notifyInitialContent(boolean)` | optional | false   | Notify current clipboard content on start.          |
| `ignoreOwnChanges(boolean)`     | optional | true    | Ignore changes made via `setContent()` methods.     |

**Important**: At least one listener is required. Calling `build()` without a listener throws `IllegalStateException`.

#### Instance Methods

| Method                              | Return                       | Description                                                                     |
|-------------------------------------|------------------------------|---------------------------------------------------------------------------------|
| `start()`                           | void                         | Starts monitoring. Must be called after `build()`. Safe to call multiple times. |
| `close()`                           | void                         | Stops monitoring, releases resources. Idempotent.                               |
| `isRunning()`                       | boolean                      | Returns true if monitor is active.                                              |
| `setContent(String)`                | void                         | Writes text to clipboard.                                                       |
| `setContent(BufferedImage)`         | void                         | Writes image to clipboard.                                                      |
| `setContent(List<File>)`            | void                         | Writes file list to clipboard.                                                  |
| `getCurrentContent()`               | Optional\<ClipboardContent\> | Reads current clipboard content. Does not require `start()`.                    |
| `getStats()`                        | Stats                        | Returns monitoring statistics.                                                  |
| `addListener(ClipboardListener)`    | void                         | Adds listener dynamically.                                                      |
| `removeListener(ClipboardListener)` | boolean                      | Removes listener. Returns true if removed.                                      |

**Note**: `start()` must be called explicitly to begin monitoring. Methods `setContent()` and `getCurrentContent()` work without calling `start()`.

---

### ClipboardListener

Functional interface for receiving clipboard changes.

```java
@FunctionalInterface
public interface ClipboardListener {
    void onClipboardChange(ClipboardContent content);
    default void onError(Exception error) { }
}
```

**Usage as lambda:**
```java
.listener(content -> System.out.println(content.getType()))
```

**Usage with error handling:**
```java
.listener(new ClipboardListener() {
    @Override
    public void onClipboardChange(ClipboardContent content) {
        // handle content
    }
    
    @Override
    public void onError(Exception error) {
        // handle error
    }
})
```

**Important**: Callbacks are invoked on a dedicated thread. Implementations must be thread-safe if accessing shared resources.

---

### ClipboardContent

Immutable representation of clipboard content. Received in listener callbacks.

#### Properties

| Property    | Type               | Description                                              |
|-------------|--------------------|----------------------------------------------------------|
| `type`      | ContentType        | Content classification (TEXT, IMAGE, FILE_LIST, UNKNOWN) |
| `timestamp` | Instant            | When content was captured                                |
| `hash`      | String             | SHA-256 hash for change detection                        |
| `size`      | long               | Content size in bytes                                    |
| `flavors`   | List\<DataFlavor\> | Available data flavors                                   |

#### Content Access Methods

| Method         | Return                    | Description                    |
|----------------|---------------------------|--------------------------------|
| `asText()`     | Optional\<String\>        | Text content if type is TEXT   |
| `asImage()`    | Optional\<BufferedImage\> | Image if type is IMAGE         |
| `asFileList()` | Optional\<List\<File\>\>  | File list if type is FILE_LIST |
| `asBytes()`    | byte[]                    | Raw bytes (defensive copy)     |

#### Getter Methods

| Method           | Return             |
|------------------|--------------------|
| `getType()`      | ContentType        |
| `getTimestamp()` | Instant            |
| `getHash()`      | String             |
| `getSize()`      | long               |
| `getFlavors()`   | List\<DataFlavor\> |

---

### ContentType

Enum for content classification.

| Value       | Description                     | Access Method  |
|-------------|---------------------------------|----------------|
| `TEXT`      | Plain text, HTML, RTF           | `asText()`     |
| `IMAGE`     | PNG, JPEG, BMP as BufferedImage | `asImage()`    |
| `FILE_LIST` | List of copied files            | `asFileList()` |
| `UNKNOWN`   | Unsupported type                | `asBytes()`    |

---

### Stats

Immutable record with monitoring statistics.

```java
public record Stats(
    long totalChanges,    // Total clipboard changes detected
    long totalErrors,     // Total errors occurred
    Instant startTime,    // When monitoring started
    Duration uptime       // Duration since start
) { }
```

---

## Exceptions

All exceptions extend `ClipboardException` (RuntimeException).

| Exception                       | When Thrown                             |
|---------------------------------|-----------------------------------------|
| `ClipboardException`            | Base exception for clipboard errors     |
| `ClipboardUnavailableException` | Clipboard locked by another application |
| `ClipboardChangedException`     | Content changed during read (transient) |

---

## Usage Examples

### Read/Write Only (No Monitoring)

For scenarios where only reading or writing clipboard content is needed without monitoring changes:

```java
// Use a no-op listener since at least one is required
try (ClipboardMonitor monitor = ClipboardMonitor.builder()
        .listener(content -> {})
        .build()) {
    
    // Read current content (no start() needed)
    Optional<ClipboardContent> content = monitor.getCurrentContent();
    content.ifPresent(c -> System.out.println("Type: " + c.getType()));
    
    // Write content (no start() needed)
    monitor.setContent("Hello World");
}
```

### Basic Monitoring

```java
try (ClipboardMonitor monitor = ClipboardMonitor.builder()
        .listener(content -> System.out.println("Changed: " + content.getType()))
        .build()) {
    monitor.start();
    Thread.sleep(60000); // Monitor for 60 seconds
}
```

### Processing Different Content Types

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(content -> {
        switch (content.getType()) {
            case TEXT -> content.asText().ifPresent(text -> {
                System.out.println("Text: " + text);
            });
            case IMAGE -> content.asImage().ifPresent(image -> {
                System.out.println("Image: " + image.getWidth() + "x" + image.getHeight());
            });
            case FILE_LIST -> content.asFileList().ifPresent(files -> {
                files.forEach(f -> System.out.println("File: " + f.getAbsolutePath()));
            });
            case UNKNOWN -> {
                System.out.println("Unknown type, raw bytes: " + content.asBytes().length);
            }
        }
    })
    .build();

monitor.start();
```

### Writing to Clipboard

```java
// Text
monitor.setContent("Hello World");

// Image
BufferedImage image = ImageIO.read(new File("image.png"));
monitor.setContent(image);

// Files
List<File> files = List.of(new File("doc1.pdf"), new File("doc2.pdf"));
monitor.setContent(files);
```

### Bidirectional Synchronization

```java
// Content written via setContent() does NOT trigger listener (anti-loop protection)
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(content -> {
        // Send to remote
        sendToRemote(content);
    })
    .build();

monitor.start();

// Receive from remote - won't trigger listener
String remoteContent = receiveFromRemote();
monitor.setContent(remoteContent);
```

### Custom Configuration

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(this::handleChange)
    .pollingInterval(Duration.ofMillis(250))  // Faster polling
    .debounce(Duration.ofMillis(50))          // Shorter debounce
    .ownershipEnabled(false)                   // Disable ownership detection
    .notifyInitialContent(true)                // Get current content on start
    .ignoreOwnChanges(false)                   // Notify all changes including own
    .build();
```

### Multiple Listeners

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(content -> logChange(content))
    .listener(content -> syncToCloud(content))
    .listener(content -> updateUI(content))
    .build();

// Or add dynamically
monitor.addListener(content -> additionalHandler(content));
monitor.removeListener(existingListener);
```

### Reading Current Content

```java
Optional<ClipboardContent> current = monitor.getCurrentContent();
current.ifPresent(content -> {
    System.out.println("Current type: " + content.getType());
});
```

### Monitoring Statistics

```java
Stats stats = monitor.getStats();
System.out.println("Changes detected: " + stats.totalChanges());
System.out.println("Errors: " + stats.totalErrors());
System.out.println("Uptime: " + stats.uptime());
```

### Error Handling

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .listener(new ClipboardListener() {
        @Override
        public void onClipboardChange(ClipboardContent content) {
            processContent(content);
        }
        
        @Override
        public void onError(Exception error) {
            if (error instanceof ClipboardUnavailableException) {
                // Clipboard locked, retry later
            } else {
                log.error("Clipboard error", error);
            }
        }
    })
    .build();
```

---

## Thread Safety

- All public methods are thread-safe
- Listeners are invoked on a dedicated callback thread
- `ClipboardContent` is immutable and safe to share between threads
- `Stats` is an immutable record

---

## Resource Management

- `ClipboardMonitor` implements `AutoCloseable`
- Always use try-with-resources or call `close()` explicitly
- `close()` is idempotent (safe to call multiple times)
- Daemon threads are used (won't prevent JVM shutdown)

---

## Required Imports

```java
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardListener;
import dev.bxlab.clipboard.monitor.ContentType;
import dev.bxlab.clipboard.monitor.internal.Stats;  // Internal API, accessed via getStats()
import dev.bxlab.clipboard.monitor.exception.ClipboardException;
import dev.bxlab.clipboard.monitor.exception.ClipboardUnavailableException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
```

---

## Logging Configuration

The library uses SLF4J for logging. A logging implementation must be provided at runtime.

### Gradle Dependency

```gradle
dependencies {
    implementation 'dev.bxlab.clipboard:clipboard-monitor:1.0.0-SNAPSHOT'
    runtimeOnly 'ch.qos.logback:logback-classic:1.5.22'  // Or any SLF4J implementation
}
```

### Logback Configuration Example

Create `src/main/resources/logback.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Set to INFO to reduce verbosity, DEBUG for troubleshooting -->
    <logger name="dev.bxlab.clipboard.monitor" level="INFO"/>

    <root level="WARN">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

### Log Levels

| Level | Information Logged                            |
|-------|-----------------------------------------------|
| DEBUG | Internal flow, state changes, hash values     |
| INFO  | Lifecycle events (start, stop, configuration) |
| WARN  | Recoverable issues, fallback behavior         |
| ERROR | Failures with exception details               |
