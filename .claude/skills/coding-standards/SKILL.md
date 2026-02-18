---
name: coding-standards
description: jhelm project coding standards and conventions for Java 21 with Lombok and Maven
user-invocable: false
---

## jhelm Coding Standards

### Style
- **Indentation**: 4 spaces
- **Naming**: Classes `PascalCase`, methods/variables `camelCase`, constants `UPPER_SNAKE_CASE`
- **Javadoc**: HTML tags for links, `{@code true}`/`{@code false}` for booleans, accurate `@param`/`@return` tags

### Lombok
- `@Getter`/`@Setter` for simple fields, `@Data` for POJOs/DTOs
- `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` for complex objects
- `@Slf4j` for logging
- `@Accessors(fluent = true)` for internal DSL-like classes

### Modern Java 21
- **Text blocks** (`"""..."""`) for multi-line strings
- **Enhanced switch** (arrow syntax)
- **Streams/Lambdas** for collection manipulation
- **try-with-resources** for system resource streams (e.g., `Files.walk()`)

### Error Handling
- SLF4J via `@Slf4j` — no `System.out.println` in production code (CLI output in `jhelm-app` is the exception)
- Descriptive exceptions; always include original cause when rethrowing
- Prefer `must*` function variants when strict validation is required

### Dependencies
- Manage versions in root `pom.xml` `<dependencyManagement>`
- Define dependency versions as properties in root `pom.xml` `<properties>` (unless managed by Spring Boot parent)
- Keep `jhelm-gotemplate` free of Spring dependencies

### Testing
- JUnit 5 (`org.junit.jupiter.api`) with `Assertions`
- `@TempDir` for temporary files
- Always run tests after code changes

### Parameterized Tests
Use `@ParameterizedTest` to avoid code duplication and loops in tests. Prefer parameterized tests over:
- Multiple near-identical `@Test` methods that differ only in input/expected values
- `for` loops inside test methods that iterate over test cases
- Copy-pasted test logic with different data

**Common sources:**
- `@CsvSource` — inline comma-separated values for simple types
- `@ValueSource` — single-argument tests with strings, ints, etc.
- `@MethodSource` — complex objects or multi-arg via static factory method returning `Stream<Arguments>`
- `@EnumSource` — iterate over enum values

**Example — prefer this:**
```java
@ParameterizedTest
@CsvSource({
    "hello, HELLO",
    "world, WORLD",
    "'', ''"
})
void testUpperCase(String input, String expected) {
    assertEquals(expected, input.toUpperCase());
}
```

**Over this:**
```java
@Test
void testUpperCase() {
    assertEquals("HELLO", "hello".toUpperCase());
    assertEquals("WORLD", "world".toUpperCase());
    assertEquals("", "".toUpperCase());
}
```

**Use `@MethodSource` for complex data:**
```java
@ParameterizedTest
@MethodSource("templateTestCases")
void testTemplateRendering(String template, Map<String, Object> data, String expected) {
    // ...
}

static Stream<Arguments> templateTestCases() {
    return Stream.of(
        Arguments.of("{{ .name }}", Map.of("name", "Alice"), "Alice"),
        Arguments.of("{{ .count }}", Map.of("count", 42), "42")
    );
}
```
