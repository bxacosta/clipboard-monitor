# Clipboard Monitor

[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.0-green)]()

Java library for monitoring system clipboard changes in real-time. Provides reliable detection of clipboard
modifications and supports text, images, and file lists.

## Quick Start

```java
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;

try (ClipboardMonitor monitor = ClipboardMonitor.builder()
        .detector(PollingDetector.defaults())
        .listener(content -> System.out.println("Clipboard changed: " + content.type()))
        .build()) {

    monitor.start();
    // Application logic here
}
```

## Features

- **Pluggable detection strategies:** Choose between `Polling` or `Ownership` strategies
- **Multiple content types:** Support for text, images, and file lists with type-safe access
- **High performance:** Virtual threads for listener isolation, single-pass clipboard read, configurable debouncing
- **Bidirectional sync ready:** Built-in loop prevention with automatic tracking of own writes

## Requirements

- Java 21+
- SLF4J API for logging

## Installation

### Gradle

```gradle
dependencies {
    implementation 'dev.bxlab.clipboard:clipboard-monitor:1.0.0'

    // SLF4J implementation (choose one)
    implementation 'ch.qos.logback:logback-classic:1.5.22'
    // or
    implementation 'org.slf4j:slf4j-simple:2.0.16'
}
```

### Maven

```xml
<dependency>
    <groupId>dev.bxlab.clipboard</groupId>
    <artifactId>clipboard-monitor</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- SLF4J implementation (choose one) -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.22</version>
</dependency>
```

---

## Usage

### Complete Monitoring

Listener exceptions are isolated to prevent monitoring interruption. Override `onError()` for custom error handling:

```java
import dev.bxlab.clipboard.monitor.*;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;

ClipboardListener listener = new ClipboardListener() {
    @Override
    public void onClipboardChange(ClipboardContent content) {
        // Process content
        if (someCondition) {
            throw new RuntimeException("Processing failed");
        }
    }

    @Override
    public void onError(Exception error) {
        log.error("Listener error: {}", error.getMessage(), error);
        // Send to monitoring system, retry, etc.
    }
};

try (ClipboardMonitor monitor = ClipboardMonitor.builder()
        .detector(PollingDetector.defaults())
        .listener(listener)
        .build()) {
    monitor.start();
}
```

### Read and Write Operations

Direct clipboard access without monitoring. Useful for simple clipboard utilities:

```java
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

try (ClipboardMonitor monitor = ClipboardMonitor.builder()
        .detector(PollingDetector.defaults())
        .listener(c -> {}) // Required by API but not used
        .build()) {

    // Write operations (no start() required)
    monitor.write("Hello from clipboard-monitor");
    monitor.write(bufferedImage);
    monitor.write(List.of(new File("file1.txt"), new File("file2.txt")));

    // Read with exception handling
    ClipboardContent content = monitor.read();

    // Read with Optional (exception-free)
    monitor.tryRead()
           .flatMap(ClipboardContent::asText)
           .ifPresent(text -> System.out.println("Current text: " + text));
}
```

**Note**: Read/write operations do not require calling `start()`. Monitor must still be built with a detector and
listener (API requirement).

---

## Configuration

### Monitor Configuration

```java
import java.time.Duration;

ClipboardMonitor.builder()
    .detector(PollingDetector.defaults())
    .listener(content -> { /* ... */ })
    .debounce(Duration.ofMillis(100))  // Custom debounce
    .notifyOnStart(true)               // Notify initial content
    .build();
```

| Parameter       | Description                                               | Default |
|-----------------|-----------------------------------------------------------|---------|
| `debounce`      | Minimum time between notifications (groups rapid changes) | 50ms    |
| `notifyOnStart` | Notify current clipboard content when `start()` is called | false   |

**Use cases**:

- **High debounce (100-200ms)**: Reduces notification frequency, useful when processing is expensive
- **Low debounce (20-50ms)**: More responsive, suitable for UI updates
- **notifyOnStart=true**: Useful for sync applications that need current state on startup

---

## Examples

The project includes runnable examples demonstrating various use cases:

| Example              | Description                               | Command                                                           |
|----------------------|-------------------------------------------|-------------------------------------------------------------------|
| **Interactive Demo** | Full-featured console with all operations | `./gradlew -q --console=plain :examples:interactive:run`          |
| **Basic Listener**   | Simple clipboard change monitoring        | `./gradlew -q --console=plain :examples:basic:runListenerExample` |
| **Basic Read**       | Reading current clipboard content         | `./gradlew -q --console=plain :examples:basic:runBasicRead`       |
| **Basic Write**      | Writing to clipboard                      | `./gradlew -q --console=plain :examples:basic:runBasicWrite`      |
| **Image Handling**   | Working with clipboard images             | `./gradlew -q --console=plain :examples:basic:runImageExample`    |
| **File Lists**       | Monitoring file copy operations           | `./gradlew -q --console=plain :examples:basic:runFileListExample` |

For detailed documentation and source code, see [examples/README.md](examples/README.md).

---

## Architecture

The library uses a layered architecture with three main components:

1. **Detector Layer:** Monitors clipboard using chosen strategy (polling or ownership-based) and detects hash changes
2. **Monitor Layer:** Debounces rapid changes, filters own writes using LRU cache, and reads stable content
3. **Listener Layer:** Each listener runs in an isolated virtual thread with independent error handling

Thread safety is guaranteed through atomic primitives, volatile fields, dedicated lock objects, and immutable data
structures.

---

## Library Reference

For comprehensive API documentation, see [llm/LIBRARY_REFERENCE.md](llm/LIBRARY_REFERENCE.md).

This reference document is optimized for LLM consumption and includes:

- Complete API reference with all methods, parameters, and return types
- Detector configuration (PollingDetector, OwnershipDetector)
- Content type implementations (TextContent, ImageContent, FilesContent, UnknownContent)
- Usage examples for common scenarios
- Thread safety and resource management details

Ideal for AI-assisted development and code generation tools.

---

## License

This project is licensed under the [MIT License](LICENSE).
