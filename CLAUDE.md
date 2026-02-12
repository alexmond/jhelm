# jhelm - Java Implementation of Helm

## Project Overview

**jhelm** is a Java implementation of the Helm package manager for Kubernetes. It provides native Java libraries and
tools to work with Helm charts without requiring the Go-based Helm CLI.

## Architecture

This is a multi-module Maven project with the following structure:

### Modules

1. **jhelm-gotemplate** - Go template engine for Java
    - Java implementation of Go's template language
    - Based on gotemplate4j (MIT License, Copyright 2021 verils)
    - Provides lexer, parser, AST nodes, and executor for Go templates
    - Includes Sprig functions and Helm-specific template functions
    - Location: `jhelm-gotemplate/`

2. **jhelm-core** - Core Helm logic and models
    - Chart management and parsing
    - Repository management
    - Template rendering engine
    - Value merging and processing
    - Location: `jhelm-core/`

3. **jhelm-kube** - Kubernetes integration
    - Kubernetes client integration
    - Resource deployment and management
    - Location: `jhelm-kube/`

4. **jhelm-app** - Application/CLI layer
    - Command-line interface built with Picocli
    - User-facing commands and workflows
    - Location: `jhelm-app/`

## Technology Stack

- **Java**: 21
- **Spring Boot**: 4.0.2
- **Kubernetes Client**: 25.0.0
- **Picocli**: 4.7.6 (CLI framework)
- **Jackson YAML**: 2.18.2
- **Commons Compress**: 1.26.1
- **Lombok**: For reducing boilerplate code

## Key Components

### Template Engine (jhelm-gotemplate)

The template engine consists of:

- **Lexer** (`internal/Lexer.java`) - Tokenizes template strings
- **Parser** (`internal/Parser.java`) - Builds AST from tokens
- **Executor** (`internal/Executor.java`) - Executes templates against data
- **AST Nodes** (`internal/ast/*.java`) - Represents template structure
- **Functions** - Built-in functions including Sprig and Helm functions

Key classes:

- `GoTemplate.java` - Main template wrapper with execution capabilities
- `GoTemplateFactory.java` - Factory for creating and managing templates
- `Functions.java` - Function registry and execution
- `HelmFunctions.java` - Helm-specific template functions

### Core Engine (jhelm-core)

Key classes:

- `Engine.java` - Main rendering engine that orchestrates template processing
- `Chart.java` - Represents a Helm chart with metadata and templates
- `RepoManager.java` - Manages Helm chart repositories

The engine handles:

- Named template collection and caching
- Subchart rendering with recursion prevention
- Value merging from chart defaults and user overrides
- Template context management (Release, Chart, Values, Capabilities, etc.)

## Working with the Codebase

### Building the Project

```bash
./mvnw clean install
```

### Module Dependencies

```
jhelm (parent)
├── jhelm-gotemplate (standalone template engine)
├── jhelm-core (depends on: jhelm-gotemplate)
├── jhelm-kube (depends on: jhelm-core)
└── jhelm-app (depends on: jhelm-core, jhelm-kube)
```

### Key Files and Locations

- **Main POM**: `pom.xml` - Parent POM with dependency management
- **Template Engine**: `jhelm-gotemplate/src/main/java/org/alexmond/jhelm/gotemplate/`
- **Core Logic**: `jhelm-core/src/main/java/org/alexmond/jhelm/core/`
- **Tests**: Each module has tests under `src/test/java/`
- **Sample Charts**: `sample-charts/` - Test Helm charts
- **Test Resources**: Template test files in `jhelm-gotemplate/src/test/resources/`

### Important Implementation Details

1. **Template Rendering**: The `Engine` class creates a new `GoTemplateFactory` for each render to avoid template
   accumulation
2. **Recursion Protection**: Stack depth tracking prevents infinite template recursion
3. **Named Templates**: `define` blocks are collected before rendering begins
4. **Value Merging**: Chart values are merged with user-provided values and release info
5. **Error Handling**: Custom exceptions for parsing, execution, and not-found scenarios

### Current Work (Branch: gotemplate4j)

The current branch includes:

- Enhanced Handlebars helpers and caching optimizations
- Stricter nesting limits for template recursion
- Improved template translation and error logging
- Chart version retrieval and repository search
- Integration tests with Helm dry-run comparison
- YAML parsing with stricter constraints

### Testing

- **Unit Tests**: Each module has comprehensive unit tests
- **Integration Tests**: `KpsComparisonTest.java` compares output with native Helm
- **Test Resources**: `charts.csv` and `charts_full.csv` contain test chart definitions
- **Template Tests**: `GoTemplateStandardTest.java`, `TemplateTest.java`

### Common Operations

**Adding a new template function**:

1. Add to `Functions.java` or `HelmFunctions.java`
2. Register in the function registry
3. Add tests in corresponding test class

**Adding chart support**:

1. Update `charts.csv` with chart details
2. Update repository prefixes in core logic
3. Add integration test cases

**Modifying template parsing**:

1. Update token types in `TokenType.java`
2. Modify lexer in `Lexer.java`
3. Update parser in `Parser.java`
4. Add corresponding AST node if needed

## Debugging Tips

- Enable detailed logging with `@Slf4j` annotations
- Check `test_output.txt` and `jhelm-core/test_output.txt` for test results
- Use `GoTemplateStandardTest` to verify template behavior
- Compare output with Helm using dry-run tests in `KpsComparisonTest`

## Repository Management

- Repository search: `RepoManager.java` handles Helm repository operations
- Chart pulling: Charts are downloaded and cached locally
- Version management: Supports specific chart version retrieval

## License

- jhelm: Project-specific license (see LICENSE file)
- jhelm-gotemplate: MIT License (forked from gotemplate4j by verils)

## Development Status

Active development on the `gotemplate4j` branch with recent commits focusing on:

- Chart compatibility improvements
- Template rendering optimization
- Error handling enhancements
- Kubernetes service integration
