# Coding Standards

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Classes | PascalCase | `ChartRepository`, `RepoManager` |
| Methods | camelCase | `installChart()`, `resolveVersion()` |
| Variables | camelCase | `chartName`, `repoUrl` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT` |
| Packages | lowercase | `org.alexmond.jhelm.core` |
| Test classes | `*Test` suffix | `ChartRepositoryTest` |
| Test methods | `test` + scenario | `testInstallWithValidChart()` |

## Lombok Patterns

### When to Use Each Annotation

```java
// DTOs and value objects - @Data for full boilerplate
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartMetadata {
	private String name;
	private String version;
	private String description;
}

// Simple field access only
@Getter
@Setter
public class ChartConfig {
	private String repoUrl;
	private int timeout;
}

// Internal DSL-style classes
@Getter
@Setter
@Accessors(fluent = true)
public class InstallOptions {
	private String releaseName;
	private String namespace;
	private boolean dryRun;
}

// Logging - always @Slf4j, never manual logger creation
@Slf4j
public class RepoManager {
	public void addRepo(String name, String url) {
		log.info("Adding repository: {} -> {}", name, url);
	}
}
```

### Lombok Guidelines

- Use `@Data` for POJOs/DTOs, not for entities with identity semantics
- Pair `@Builder` with `@NoArgsConstructor` and `@AllArgsConstructor` for framework compatibility
- Prefer `@Getter`/`@Setter` over `@Data` when you only need accessors
- Use `@Accessors(fluent = true)` for builder-like internal APIs
- Never mix manual getters/setters with Lombok on the same class

## Modern Java 21

### Text Blocks

```java
// Use text blocks for multi-line strings
String template = """
		apiVersion: v1
		kind: ConfigMap
		metadata:
		  name: {{ .Release.Name }}
		""";

// Not this
String template = "apiVersion: v1\n" +
	"kind: ConfigMap\n" +
	"metadata:\n" +
	"  name: {{ .Release.Name }}\n";
```

### Enhanced Switch

```java
// Arrow syntax for clean control flow
String description = switch (chartType) {
	case APPLICATION -> "Deployable application";
	case LIBRARY -> "Reusable library chart";
	default -> "Unknown chart type";
};

// Multi-statement blocks when needed
return switch (action) {
	case INSTALL -> {
		validateChart(chart);
		yield performInstall(chart, values);
	}
	case UPGRADE -> {
		validateRelease(release);
		yield performUpgrade(release, chart);
	}
};
```

### Streams and Lambdas

```java
// Prefer streams for collection transformations
List<String> chartNames = charts.stream()
		.filter(c -> c.getVersion() != null)
		.map(Chart::getName)
		.sorted()
		.toList();

// Use method references where clear
files.stream()
		.filter(Files::isRegularFile)
		.forEach(this::processFile);
```

### Try-With-Resources

```java
// Always use for system resource streams
try (var stream = Files.walk(chartDir)) {
	return stream.filter(Files::isRegularFile)
			.filter(p -> p.toString().endsWith(".yaml"))
			.toList();
}

// Multiple resources
try (var input = new FileInputStream(source);
		var output = new FileOutputStream(target)) {
	input.transferTo(output);
}
```

## Error Handling

### Exception Guidelines

```java
// Include original cause when rethrowing
try {
	chart = loadChart(path);
}
catch (IOException ex) {
	throw new ChartLoadException("Failed to load chart: " + path, ex);
}

// Descriptive messages with context
throw new IllegalArgumentException(
		"Chart version '%s' not found in repository '%s'".formatted(version, repoName));

// Never swallow exceptions silently
try {
	cleanup(tempDir);
}
catch (IOException ex) {
	log.warn("Failed to clean up temporary directory: {}", tempDir, ex);
}
```

### Logging Best Practices

```java
@Slf4j
public class ChartService {

	public void install(String name) {
		log.debug("Installing chart: {}", name);         // Details
		log.info("Chart installed: {}", name);            // Important events
		log.warn("Chart deprecated: {}", name);           // Recoverable issues
		log.error("Chart installation failed: {}", name, ex);  // Failures + exception
	}

}
```

| Level | Use For |
|-------|---------|
| `debug` | Detailed flow, variable values, diagnostics |
| `info` | Important business events, state transitions |
| `warn` | Recoverable problems, deprecations |
| `error` | Failures requiring attention; always include exception |

### Rules

- Use SLF4J parameterized messages (`{}`) -- never string concatenation
- Pass exception as the last argument to preserve stack trace
- No `System.out.println` in production code (CLI output in `jhelm-app` is the exception)
- Log at the right level; do not log and rethrow the same exception

## Import Style

```java
// Always use import statements
import java.util.Locale;
import java.util.Map;

// Use the imported class directly
String lower = name.toLowerCase(Locale.ROOT);
Map<String, Object> values = loadValues();

// NEVER use inline fully qualified names
// BAD: name.toLowerCase(java.util.Locale.ROOT);
// BAD: java.util.Map<String, Object> values = loadValues();
```

- No star imports (`import java.util.*`)
- No inline fully qualified names (PMD `UnnecessaryFullyQualifiedName`)
- Fix with: `python3 .claude/scripts/fix_fqn.py .`

## Dependency Management

### Maven Conventions

```xml
<!-- Define versions as properties in root pom.xml -->
<properties>
    <picocli.version>4.7.6</picocli.version>
    <jackson-yaml.version>2.18.2</jackson-yaml.version>
</properties>

<!-- Manage versions in root dependencyManagement -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>info.picocli</groupId>
            <artifactId>picocli</artifactId>
            <version>${picocli.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Child modules omit version (inherited from parent) -->
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli</artifactId>
</dependency>
```

- Version properties in root `pom.xml` `<properties>` (unless managed by Spring Boot parent)
- All version management in root `<dependencyManagement>`
- Child modules never declare versions directly
- Do not add version-less entries to `<dependencyManagement>` (overrides Spring Boot BOM with null)

## Formatting

- **Indentation**: Tabs (enforced by `spring-javaformat-maven-plugin`)
- **Formatter**: Runs on `validate` phase automatically
- **Auto-fix**: `./mvnw spring-javaformat:apply`
- **Validation**: `./mvnw validate` catches formatting, checkstyle, and PMD violations
