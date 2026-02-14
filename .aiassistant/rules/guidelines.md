# Project Coding Guidelines

This document outlines the mandatory coding standards and preferences for the `jhelm` project.

## 1. General Principles
- **Consistency**: Always match the style and patterns of the existing codebase.
- **Modern Java**: Use Java 21 features wherever appropriate.
- **Minimalism**: Keep implementations focused and avoid unnecessary dependencies.

## 2. Style and Formatting
- **Indentation**: Use 4 spaces for indentation.
- **Naming**: 
    - Classes: `PascalCase`
    - Methods and Variables: `camelCase`
    - Constants: `UPPER_SNAKE_CASE`
- **Javadoc**:
    - Use HTML tags (e.g., `<a>`) for links in Javadoc.
    - Use `{@code true}` or `{@code false}` when referring to boolean values.
    - Ensure `@param` and `@return` tags are accurate and refer to existing parameters/return types.

## 3. Boilerplate Reduction (Lombok)
- **Prefer Lombok**: Use Lombok annotations to reduce boilerplate code.
    - Use `@Getter` and `@Setter` for simple fields.
    - Use `@Data` for POJOs and DTOs.
    - Use `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor` for complex objects and data models (e.g., `Release.java`).
    - Use `@Slf4j` for logging.
- **Fluent Accessors**: Use `@Accessors(fluent = true)` for internal DSL-like classes (e.g., `Token.java`).

## 4. Modern Java Features
- **Text Blocks**: Use text blocks (`"""..."""`) for multi-line strings, SQL queries, templates, and complex placeholders.
- **Enhanced Switch**: Use the enhanced `switch` expression (arrow syntax) for better readability and exhaustiveness.
- **Streams and Lambdas**: Use Java Streams for collection manipulation. Prefer `List.addAll()` or `Collectors.toList()` over manual loops.
- **Resource Management**: Always use try-with-resources for managing streams that wrap system resources (e.g., `Files.walk()`).

## 5. Testing
- **Framework**: Use JUnit 5 (`org.junit.jupiter.api`) for all tests.
- **Assertions**: Use `org.junit.jupiter.api.Assertions`.
- **Integration Tests**: 
    - Integration tests that compare output with external tools (like Helm) should be clearly marked and may use `target/` for temporary artifacts.
    - Use `@TempDir` for managing temporary files during tests.
- **Coverage**: Add tests for new features and bug fixes. Ensure core logic is covered by unit tests.

## 6. Error Handling and Logging
- **Logging**: Use SLF4J via Lombok's `@Slf4j`. Avoid `System.out.println` in production code; use it only for CLI output in the `app` module.
- **Exceptions**: 
    - Throw descriptive exceptions (e.g., `RuntimeException` with clear messages or custom project exceptions like `TemplateParseException`).
    - When rethrowing, always include the original cause.
- **Safety**: Prefer "must" variants of functions (e.g., `mustToYaml`) that throw exceptions on error when strict validation is required.

## 7. Project Structure
- **Maven**: Adhere to the standard Maven project structure.
- **Modules**:
    - `jhelm-gotemplate`: Core Go template engine implementation.
    - `jhelm-core`: Helm-specific logic, charts, and actions.
    - `jhelm-kube`: Kubernetes client and provider implementations.
    - `jhelm-app`: CLI entry point and Spring Boot configuration.
- **Dependencies**: 
    - Manage versions in the root `pom.xml` using `<dependencyManagement>`.
    - Always define dependency versions as properties in the `<properties>` section of the root `pom.xml`, unless the version is already managed by the Spring Boot parent.
