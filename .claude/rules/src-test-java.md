---
globs: **/src/test/java/**/*.java
---

# Test Code Rules

- Use JUnit 5 (`org.junit.jupiter.api`) with `Assertions` — NOT AssertJ
- Prefer real test data (chart YAML, Go template strings, `@TempDir` files) over Mockito mocks
- Mockito is acceptable only for HTTP (`CloseableHttpClient`) and Kubernetes (`KubeService`)
- Use `@TempDir` for temporary files — never hardcode temp paths
- Use `@ParameterizedTest` with `@CsvSource` or `@MethodSource` to avoid test duplication
- Descriptive test method names: `testCommandSuccess`, `testCommandWithError`
- Test charts live in `src/test/resources/test-charts/<name>/`
