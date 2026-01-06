# Clipboard Monitor - Development Guide

Java library for real-time system clipboard monitoring. Supports text, images, and file lists.

## Code Philosophy

- Favor simple and elegant solutions over complex abstractions
- Apply the Single Responsibility Principle consistently
- Maintain a linear code flow that is simple to follow and reason about
- Use design patterns flexibly when they simplify code and reduce redundancy
- Avoid overengineering and unnecessary abstractions
- Prioritize code consistency, modularity, and maintainability
- Favor self-documented code over Javadoc

## Research and External Resources

When implementation details are unclear or dependencies need to be added:

- Use web search tools to find current best practices and API usage
- Query Context7 MCP Tool for official library documentation
- Verify latest stable versions before adding dependencies
- Search for modern Java capabilities and idiomatic coding standards

## Commands

```shell
# compile, test, package
./gradlew clean build

 # run tests only
./gradlew test --rerun   
```

## Package Structure

- `core/` - Public API and monitoring infrastructure
- `content/` - Content type implementations
- `detector/` - Detection strategies
- `exception/` - Custom exceptions extending ClipboardException
- `transferable/` - AWT Transferable implementations
- `util/` - Utility classes

## Class Design

- Each class has a single, well-defined responsibility
- DTOs and value objects must be immutable (use record when possible)
- Utility classes use private constructor and final modifier
- Keep class size manageable; prefer multiple focused classes over large ones

## Lombok

- `@Slf4j` for logging in all classes that require it
- `@Getter` only; never use `@Setter` on immutable classes
- `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with explicit `@Include` on identity fields
- `@NoArgsConstructor(access = AccessLevel.PRIVATE)` for utility classes
- Avoid `@Builder` when custom validation or defensive copies are needed in construction

### Builder Pattern

Use custom Builders for type-safe instantiation and complex validation:

- **Structure:** Private constructor with fluent setters returning `this`.
- **Validation:** Use `build()` to enforce `Objects.requireNonNull()` and domain constraints.

## Thread Safety

Concurrency primitives:

- `AtomicBoolean` for state flags (running, closed)
- `AtomicReference<T>` for reference updates
- `CopyOnWriteArrayList<T>` for listener collections
- Dedicated lock object for synchronized blocks (never `synchronized(this)`)

## Null Handling

- Validate required parameters: `Objects.requireNonNull(param, "descriptive message")`
- Return `Optional.empty()` instead of null for nullable results
- Return empty collections instead of null
- Never return null from public API methods

## Exceptions

- All custom exceptions extend `ClipboardException` (which extends RuntimeException)
- Include context in exception messages
- Use `(String message, Throwable cause)` constructor when wrapping exceptions

## Logging

Log levels (library context):

- DEBUG: internal flow, state changes, hash values
- WARN: recoverable issues, fallback behavior
- ERROR: failures with an exception object

Avoid INFO logs in library code. INFO is reserved for applications, not libraries.

Use placeholder syntax: `log.debug("value: {}", value)`

## Testing

Framework: JUnit 5 + AssertJ + Awaitility

Structure:

- Use Given/When/Then structure in the test body
- `assertThat()` from AssertJ for assertions
- `assertThatThrownBy()` for exception testing
- Test method names describe expected behavior
- Methods: `should*()` (shouldCreateTextContent)

## Documentation

Javadoc requirements:

- Required for all public classes and methods
- Be simple, concise, and direct, explain what it does
- Avoid technical justifications or implementation details
- Document `@param`, `@return`, and `@throws`

## Design Patterns

Apply patterns flexibly when they simplify the structure:

- Sealed interfaces for closed type hierarchies
- AutoCloseable for resource management (close() must be idempotent)
- Record for immutable data containers
- @FunctionalInterface for single-method interfaces with optional defaults
- Builder pattern for classes with multiple optional parameters
- Strategy pattern for pluggable behavior
- Pattern matching with instanceof for type-safe casting

Apply patterns pragmatically; avoid forcing patterns where simple code suffices.

## Dependencies

Dependency philosophy:

- Minimize external dependencies
- Prefer standard library solutions when sufficient
- Verify latest stable version before adding new dependencies
- Use research tools to understand proper usage patterns
- Justify new dependencies based on maintainability and value
