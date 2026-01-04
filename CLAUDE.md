# Clipboard Monitor - Code Conventions

Java 21 | Package: `dev.bxlab.clipboard.monitor` | Gradle 9.x + Lombok

```bash
./gradlew clean build    # compile, test, package
./gradlew test --rerun   # run tests only
```

## Project Structure

- `[root package]` → Public API: `ClipboardMonitor`, `ClipboardContent`, `ClipboardListener`, `ContentType`
- `exception/` → Custom exceptions extending `ClipboardException`
- `internal/` → NOT public API (detectors, guards, readers, stats)
- `transferable/` → Clipboard Transferable implementations
- `util/` → Utility classes

---

## Class Design

- All public classes are `final` unless designed for extension
- DTOs and value objects must be immutable
- Utility classes: private constructor + `final` class
- Internal implementation goes in `internal/` package

## Lombok

- `@Slf4j` for all classes that need logging
- `@Getter` only (never `@Setter`) for immutable classes
- `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with explicit `@Include` on identity fields
- `@NoArgsConstructor(access = AccessLevel.PRIVATE)` for utility classes
- Avoid `@Builder` when custom validation or type-safe factory methods are needed

## Builder Pattern

- Use custom Builder when: validation in `build()`, type-safe factory methods, or defensive copies needed
- Builder constructor is private
- All setter methods return `this` for chaining
- Validate required fields with `Objects.requireNonNull()` in `build()`
- Validate constraints (positive intervals, non-empty lists) in `build()`

## Thread Safety

- `AtomicBoolean` for state flags (running, closed)
- `AtomicReference<T>` for reference updates
- `CopyOnWriteArrayList<T>` for listener collections
- `volatile` for shared mutable state
- Dedicated lock object for `synchronized` blocks (never `synchronized(this)`)
- Dedicated `ExecutorService` for async callbacks

## Immutability & Defensive Copies

- Use `List.copyOf()` for collections in constructors and builders
- Use `array.clone()` for arrays in constructors and getters
- Return `Optional.empty()` instead of `null`
- Return empty collections instead of `null`

## Null Handling

- Validate required parameters with `Objects.requireNonNull(param, "message")`
- Return `Optional<T>` for nullable results
- Never return `null` from public methods

## Exceptions

- All custom exceptions extend `ClipboardException` (which extends `RuntimeException`)
- Always include context in exception messages
- Use constructor with `(String message, Throwable cause)` when wrapping

## Time APIs

- `System.nanoTime()` for intervals and elapsed time measurement
- `Instant.now()` for timestamps and wall-clock time
- `java.time.Duration` for duration parameters in APIs

## Logging

- DEBUG: internal flow, state changes, hash values
- INFO: lifecycle events (start, stop, configuration)
- WARN: recoverable issues, fallback behavior
- ERROR: failures with exception object
- Use placeholder syntax: `log.debug("value: {}", value)`

---

## Naming Conventions

- Utility classes: `*Utils` (e.g., `HashUtils`, `ImageUtils`)
- Detectors: `*Detector` (e.g., `PollingDetector`)
- Transferables: `*Transferable` (e.g., `ImageTransferable`)
- Exceptions: `*Exception` (e.g., `ClipboardChangedException`)
- Test classes: `*Test` (e.g., `ClipboardMonitorTest`)
- Test methods: `should*()` (e.g., `shouldCreateTextContent()`)

---

## Testing

- Framework: JUnit 5 + AssertJ
- Use Given/When/Then structure in test body
- Use `assertThat()` from AssertJ for assertions
- Use `assertThatThrownBy()` for exception testing
- Test method names describe expected behavior

---

## Documentation

- Javadoc required for all public classes and methods
- Use `{@code}` blocks for inline code in Javadoc
- Use `<pre>{@code ... }</pre>` for code examples
- Document `@param`, `@return`, and `@throws`

---

## Patterns to Follow

- Implement `AutoCloseable` for classes managing resources; `close()` must be idempotent
- Use `record` for immutable data containers
- Use `@FunctionalInterface` for single-method interfaces with optional defaults
- Use pattern matching with `instanceof` for type-safe casting
- Return `Optional<T>` with type check before extracting value

## Anti-Patterns to Avoid

- `@Setter` on DTOs → use immutable with builder
- `return null` → use `Optional.empty()` or empty collection
- Catch and ignore exceptions → log at minimum
- Public mutable fields → private final + getter
- `new ArrayList<>(list)` for immutable copy → use `List.copyOf()`
- `Thread.sleep()` → use `ScheduledExecutorService`
- String concatenation in logs → use placeholder `{}`
- `synchronized(this)` → use dedicated lock object
