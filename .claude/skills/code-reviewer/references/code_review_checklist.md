# Code Review Checklist

## Pre-Review Checks

| Check | Details |
|-------|---------|
| Build passes | `./mvnw clean install` completes without errors |
| Tests pass | All existing tests pass; new tests added for new code |
| Formatting | `./mvnw spring-javaformat:apply` applied; no checkstyle violations |
| PMD clean | No new PMD violations (`./mvnw validate`) |
| PR hygiene | Clear title, description explains *why*, linked issue if applicable |
| Scope | PR does one thing; no unrelated changes mixed in |

## Correctness

- [ ] Logic handles all branches (happy path, edge cases, error cases)
- [ ] Null values handled appropriately (no unguarded dereferences)
- [ ] Collections checked for empty before access; `Optional` used where appropriate
- [ ] Boundary conditions correct (off-by-one, empty input, max values)
- [ ] Concurrency: shared mutable state protected or avoided
- [ ] Data transformations preserve invariants (no silent data loss)
- [ ] API contracts match documentation and caller expectations

## Error Handling

- [ ] Exceptions are descriptive and include the original cause when rethrowing
- [ ] Checked exceptions caught at the right level (not swallowed silently)
- [ ] Resources closed in `finally` or try-with-resources
- [ ] Error messages help diagnose the problem (include relevant context)
- [ ] No empty catch blocks without explicit justification

## Security

- [ ] User input validated before use (path traversal, injection)
- [ ] No secrets or credentials in code or logs
- [ ] Deserialization of untrusted data uses safe parsers (Jackson with type restrictions)
- [ ] File paths canonicalized and validated against allowed directories
- [ ] HTTP responses validated; no blind trust of external data
- [ ] Dependencies checked for known vulnerabilities

## Performance

- [ ] No unnecessary object creation in loops
- [ ] Collections pre-sized when size is known
- [ ] Streams used appropriately (not for trivial single-element operations)
- [ ] No N+1 patterns (repeated calls inside loops for data that could be batched)
- [ ] Large data sets processed with streaming, not loaded entirely into memory
- [ ] String concatenation in loops uses `StringBuilder`

## Maintainability

- [ ] Methods are short and focused (under 50 lines preferred, 80 max)
- [ ] Classes have a single responsibility
- [ ] Names are descriptive and follow conventions
- [ ] No duplicated logic (DRY, but not over-abstracted)
- [ ] Complex logic has comments explaining *why*, not *what*
- [ ] No magic numbers; constants extracted with meaningful names

## Testing

- [ ] New code has corresponding tests
- [ ] Tests cover happy path, edge cases, and error conditions
- [ ] Tests are independent (no shared mutable state between tests)
- [ ] Test names describe the scenario being verified
- [ ] Real test data preferred over mocks (chart YAML, template strings, `@TempDir`)
- [ ] Mockito used only for HTTP clients and Kubernetes services
- [ ] `@ParameterizedTest` used to avoid duplicated test logic

## Java-Specific Checks

### Lombok

```java
// Preferred patterns
@Data                          // POJOs and DTOs
@Builder                       // Complex object construction
@Getter @Setter                // Simple field access
@Slf4j                         // Logging (never System.out in production)
@Accessors(fluent = true)      // Internal DSL-style classes
@NoArgsConstructor @AllArgsConstructor  // When needed with @Builder
```

- [ ] Lombok annotations match the use case (not `@Data` on entities with identity)
- [ ] `@Builder` combined with `@NoArgsConstructor`/`@AllArgsConstructor` when needed

### Import Style

```java
// Correct
import java.util.Locale;
name.toLowerCase(Locale.ROOT);

// Wrong - never use inline fully qualified names
name.toLowerCase(java.util.Locale.ROOT);
```

- [ ] No fully qualified class names used inline (PMD `UnnecessaryFullyQualifiedName`)
- [ ] No star imports (expand to specific classes)

### Logging

```java
// Correct - SLF4J via Lombok
@Slf4j
public class MyService {
    public void process() {
        log.debug("Processing item: {}", itemId);
        log.error("Failed to process", exception);
    }
}
```

- [ ] `@Slf4j` used for logging; no `System.out.println` in production code
- [ ] Log level appropriate (debug for details, warn for recoverable, error for failures)
- [ ] Exceptions passed as last argument to `log.error()`, not stringified

### Java 21 Features

```java
// Text blocks for multi-line strings
String yaml = """
        apiVersion: v1
        kind: ConfigMap
        """;

// Enhanced switch
String result = switch (action) {
    case INSTALL -> handleInstall();
    case UPGRADE -> handleUpgrade();
    default -> throw new IllegalArgumentException("Unknown: " + action);
};
```

- [ ] Text blocks used for multi-line strings (YAML, templates, SQL)
- [ ] Enhanced switch (arrow syntax) used for readability
- [ ] Streams and lambdas used for collection manipulation
- [ ] Records considered for immutable data carriers

### Resource Management

```java
// Correct - try-with-resources
try (var stream = Files.walk(chartDir)) {
    stream.filter(Files::isRegularFile).forEach(this::process);
}
```

- [ ] `Files.walk()`, `Files.list()`, streams from I/O use try-with-resources
- [ ] `Closeable` resources not leaked

### JUnit 5 Patterns

```java
@Test
void testInstallWithValidChart() { ... }

@ParameterizedTest
@CsvSource({"stable,https://charts.helm.sh/stable", "bitnami,https://charts.bitnami.com"})
void testRepoAdd(String name, String url) { ... }

@Test
void testInstallFailsWithInvalidChart(@TempDir Path tempDir) { ... }
```

- [ ] JUnit 5 annotations (`@Test`, `@ParameterizedTest`, `@BeforeEach`)
- [ ] `Assertions` from `org.junit.jupiter.api`
- [ ] `@TempDir` for temporary files
- [ ] Descriptive test method names
