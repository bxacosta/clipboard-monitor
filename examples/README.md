# Examples

This directory contains example applications demonstrating the usage of the Clipboard Monitor library.

## Modules

| Module                      | Description                                           |
|-----------------------------|-------------------------------------------------------|
| [basic](basic/)             | Simple, single-purpose examples for common operations |
| [interactive](interactive/) | Full-featured interactive console application         |

## Running Examples

All examples are executed via Gradle tasks from the project root directory.

### Basic Examples

```bash
./gradlew -q --console=plain :examples:basic:runBasicRead
./gradlew -q --console=plain :examples:basic:runBasicWrite
./gradlew -q --console=plain :examples:basic:runImageExample
./gradlew -q --console=plain :examples:basic:runFileListExample
./gradlew -q --console=plain :examples:basic:runStatsExample
./gradlew -q --console=plain :examples:basic:runListenerExample
```

### Interactive Demo

```bash
./gradlew -q --console=plain :examples:interactive:run
```

## Requirements

- Java 17 or higher
- Gradle 9.x

For detailed information about each example, refer to the README file in the respective module directory.
