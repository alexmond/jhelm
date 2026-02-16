# CLAUDE.md - jhelm Project Guide

## Project Overview

jhelm is a Java implementation of the Helm package manager for Kubernetes. It provides native Java libraries and tools to work with Helm charts without requiring the Go-based Helm CLI.

**Tech Stack**: Java 21, Spring Boot 4.0.2, Kubernetes Client 25.0.0, Picocli 4.7.6, Jackson YAML 2.18.2, Lombok

## Project Structure

```
jhelm (parent)
├── jhelm-gotemplate/  — Go template engine (standalone, no Spring dependency)
├── jhelm-core/        — Helm-specific logic, charts, actions (depends on: jhelm-gotemplate)
├── jhelm-kube/        — Kubernetes client integration (depends on: jhelm-core)
├── jhelm-app/         — CLI entry point, Spring Boot + Picocli (depends on: jhelm-core, jhelm-kube)
└── doc/               — Antora documentation (AsciiDoc)
```

## Build & Test Commands

```bash
# Build the project
./mvnw clean install

# Run all tests
./mvnw test

# Run tests for a specific module
./mvnw test -pl jhelm-core

# Run a specific test class
./mvnw test -Dtest=KpsComparisonTest -pl jhelm-core

# Run a specific test method
./mvnw test -Dtest=KpsComparisonTest#testSimpleRendering -pl jhelm-core
```

**Always run relevant tests after making code changes.** Fix any test failures before committing.

## Coding Standards

### Style
- **Indentation**: 4 spaces
- **Naming**: Classes `PascalCase`, methods/variables `camelCase`, constants `UPPER_SNAKE_CASE`
- **Javadoc**: Use HTML tags for links, `{@code true}`/`{@code false}` for booleans, accurate `@param`/`@return` tags

### Lombok
- Use `@Getter`/`@Setter` for simple fields, `@Data` for POJOs/DTOs
- Use `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` for complex objects
- Use `@Slf4j` for logging
- Use `@Accessors(fluent = true)` for internal DSL-like classes

### Modern Java (21)
- **Text blocks** (`"""..."""`) for multi-line strings
- **Enhanced switch** (arrow syntax) for readability
- **Streams/Lambdas** for collection manipulation
- **try-with-resources** for system resource streams (e.g., `Files.walk()`)

### Error Handling & Logging
- Use SLF4J via `@Slf4j` — no `System.out.println` in production code (CLI output in `jhelm-app` is the exception)
- Throw descriptive exceptions; always include original cause when rethrowing
- Prefer `must*` function variants (e.g., `mustToYaml`) when strict validation is required

### Dependencies
- Manage versions in root `pom.xml` `<dependencyManagement>`
- Define dependency versions as properties in root `pom.xml` `<properties>` (unless managed by Spring Boot parent)
- Keep `jhelm-gotemplate` free of Spring dependencies

## Key Architecture

### Template Engine (`jhelm-gotemplate`)
- Lexer → Parser → AST → Executor pipeline
- Key classes: `GoTemplate`, `Functions`
- Functions organized by category: Sprig (strings, collections, logic, math, encoding, crypto, date, reflection, network, semver) and Helm (conversion, template, kubernetes, chart)

### Core Engine (`jhelm-core`)
- `Engine.java` — orchestrates template processing
- `Chart.java` — represents Helm charts
- `RepoManager.java` — manages chart repositories
- Creates new `GoTemplate` per render to avoid template accumulation
- Stack depth tracking prevents infinite template recursion

### Adding a New Template Function
1. Choose category: Helm (`helm/conversion/`, `helm/template/`, `helm/kubernetes/`, `helm/chart/`) or Sprig (`sprig/strings/`, `sprig/collections/`, etc.)
2. Add function to the appropriate category class
3. Function auto-registers via coordinator (`HelmFunctions` or `SprigFunctionsRegistry`)
4. Add tests in corresponding test class
5. Update `getFunctionCategories()` in coordinator if adding a new category

## Testing
- **Framework**: JUnit 5 (`org.junit.jupiter.api`)
- **Assertions**: `org.junit.jupiter.api.Assertions`
- Use `@TempDir` for temporary files in tests
- Integration tests comparing with Helm output use `target/` for temp artifacts
- Key test classes: `GoTemplateStandardTest`, `TemplateTest`, `KpsComparisonTest`, `Helm4FunctionsTest`
