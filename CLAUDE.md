# CLAUDE.md - jhelm Project Guide

## Project Overview

jhelm is a Java implementation of the Helm package manager for Kubernetes. It provides native Java libraries and tools to work with Helm charts without requiring the Go-based Helm CLI.

**Tech Stack**: Java 21, Spring Boot 4.0.2, Kubernetes Client 25.0.0, Picocli 4.7.6, Jackson YAML 2.18.2, Lombok

## Project Structure

```
jhelm (parent)
├── jhelm-gotemplate/  — Go template engine
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
- **Indentation**: Tabs (enforced by `spring-javaformat-maven-plugin`)
- **Formatter**: `spring-javaformat` runs on every `validate` phase — run `./mvnw spring-javaformat:apply` to auto-format
- **Naming**: Classes `PascalCase`, methods/variables `camelCase`, constants `UPPER_SNAKE_CASE`
- **Imports**: Always use `import` statements — never use fully qualified class names inline in code (e.g., use `Locale.ROOT` not `java.util.Locale.ROOT`). Enforced by PMD `UnnecessaryFullyQualifiedName` rule. Fix script: `python3 .claude/scripts/fix_fqn.py .`
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
- **Prefer real test data** (chart YAML, Go template strings, `@TempDir` files) over Mockito mocks
- Test charts live in `src/test/resources/test-charts/<name>/` — use them for Engine/template tests
- Mockito is acceptable only for HTTP (`CloseableHttpClient`) and Kubernetes (`KubeService`) where a live server is unavailable

## PMD

**Plugin**: `maven-pmd-plugin` 3.26.0 (PMD 7.7.0). Violations **fail the build** at `validate` phase.

**Config**: `pmd-ruleset.xml` at project root. Excludes rules that don't fit the codebase (complexity rules for state machines, CLI-specific patterns, etc.).

### Fixing violations

```bash
# Check PMD violations
./mvnw validate 2>&1 | grep "PMD Failure"

# Fix fully qualified names (most common violation)
python3 .claude/scripts/fix_fqn.py .

# Then format and re-validate
./mvnw spring-javaformat:apply && ./mvnw validate
```

### Common rules to be aware of
- `UnnecessaryFullyQualifiedName` — use imports, not inline FQN
- `AppendCharacterWithChar` — use `.append('x')` not `.append("x")` for single chars
- `UseLocaleWithCaseConversions` — use `.toLowerCase(Locale.ROOT)` not `.toLowerCase()`
- `RedundantFieldInitializer` — don't write `boolean x = false` (false is the default)
- `MissingOverride` — add `@Override` on interface/superclass method implementations

## Checkstyle

**Plugin**: `maven-checkstyle-plugin` 3.6.0 with `spring-javaformat-checkstyle` 0.0.43. Violations **fail the build** at `validate` phase.

### Checking violations

```bash
# Check all modules
./mvnw validate 2>&1 | grep -E "violations|ERROR.*\.java"

# Check specific module
./mvnw validate -pl jhelm-core 2>&1 | grep "^\[ERROR\]"
```

### Fixing violations

1. **Auto-format first** (fixes indentation, spacing):
   ```bash
   ./mvnw spring-javaformat:apply
   ```

2. **Run the fix script** (handles SpringCatch, NeedBraces, SpringLambda, SpringTernary, star imports):
   ```bash
   # Script is embedded in /.claude/skills/checkstyle/skill.md
   python3 /tmp/fix_violations.py
   ```

3. **Manual fixes** for: `InnerTypeLast` (move inner classes after all methods), `SpringHideUtilityClassConstructor` (add private constructor + `final`), `NestedIfDepth` (extract method or suppress), `AnnotationUseStyle` (remove trailing `,` in annotation arrays)

4. **Re-validate**:
   ```bash
   ./mvnw spring-javaformat:apply && ./mvnw validate
   ```

### Size limits

Configured in `checkstyle.xml` (project root):

| Scope | Consider refactoring | Build fails |
|---|---|---|
| File | > 500 lines | > 1000 lines |
| Method | > 50 lines | > 80 lines |

### Suppressions

`checkstyle-suppressions.xml` at project root. Currently suppresses:
- `SpringHeader`, `SpringTestFileName`, `JavadocPackage`, all `Javadoc*`/`SpringJavadoc` — project conventions differ
- `RegexpSinglelineJava` — JUnit 5 assertions used instead of AssertJ
- `SpringImportOrder` — handled by `spring-javaformat:apply`
- `RequireThis` — not enforced
- `SpringMethodVisibility` for `KpsComparisonTest.java` — TrustManager interface requires public
- `NestedIfDepth` for `RepoManager.java` — intentional deep YAML parsing
- `MethodLength` for `Lexer.java`, `Parser.java` — state machines; pattern requires long dispatch methods
- `MethodLength`+`FileLength` for `HelmChartTemplates.java` — static YAML text blocks, not logic
- `MethodLength`+`FileLength` for `*Test.java` — test files exempt from size limits
