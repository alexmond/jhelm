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
