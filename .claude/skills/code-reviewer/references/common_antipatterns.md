# Common Antipatterns

## Structural Antipatterns

### God Class

A class that does too much -- too many fields, too many methods, too many responsibilities.

```java
// BAD - one class handling everything
public class ChartManager {
	// Repository management, chart loading, template rendering,
	// Kubernetes deployment, logging, caching... all in one class
}

// GOOD - separate responsibilities
public class ChartRepository { /* repo operations */ }
public class ChartLoader { /* chart parsing */ }
public class TemplateRenderer { /* template processing */ }
```

**Detection**: File exceeds 500 lines, or class name includes "Manager", "Processor", "Handler" with many unrelated methods.

### Long Method

Methods that exceed 50 lines become hard to understand, test, and maintain.

```java
// BAD - 80+ line method doing multiple things
public void installChart(String name, Map<String, Object> values) {
	// validate input (15 lines)
	// resolve chart (20 lines)
	// render templates (15 lines)
	// apply to cluster (20 lines)
	// log results (10 lines)
}

// GOOD - extract meaningful steps
public void installChart(String name, Map<String, Object> values) {
	validate(name, values);
	Chart chart = resolveChart(name);
	List<String> manifests = renderTemplates(chart, values);
	applyManifests(manifests);
}
```

### Deep Nesting

More than 3 levels of nesting signals a need for extraction or early returns.

```java
// BAD
if (chart != null) {
	if (chart.getMetadata() != null) {
		for (Dependency dep : chart.getDependencies()) {
			if (dep.isEnabled()) {
				if (dep.getVersion() != null) {
					// actual logic buried here
				}
			}
		}
	}
}

// GOOD - guard clauses and streams
if (chart == null || chart.getMetadata() == null) {
	return;
}
chart.getDependencies().stream()
		.filter(Dependency::isEnabled)
		.filter(dep -> dep.getVersion() != null)
		.forEach(this::processDependency);
```

### Magic Numbers

Unexplained literal values make code fragile and hard to understand.

```java
// BAD
if (retryCount > 3) { ... }
Thread.sleep(5000);
if (name.length() > 253) { ... }

// GOOD
private static final int MAX_RETRIES = 3;
private static final long RETRY_DELAY_MS = 5000;
private static final int MAX_KUBERNETES_NAME_LENGTH = 253;
```

### Primitive Obsession

Using raw primitives or strings where a domain type would be safer.

```java
// BAD - stringly typed
public void install(String chartName, String version, String repo, String namespace) { ... }

// GOOD - domain types prevent parameter mixups
public void install(ChartRef chartRef, Namespace namespace) { ... }

@Builder
public record ChartRef(String name, String version, String repository) { }
```

## Security Antipatterns

### Unsafe YAML Deserialization

```java
// BAD - deserializing arbitrary types from untrusted YAML
ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
Object result = mapper.readValue(untrustedInput, Object.class);

// GOOD - deserialize to a specific, known type
ChartMetadata metadata = mapper.readValue(input, ChartMetadata.class);

// GOOD - configure mapper to restrict types
mapper.activateDefaultTyping(
		mapper.getPolymorphicTypeValidator(),
		DefaultTyping.NON_FINAL);
```

### Path Traversal

```java
// BAD - user input used directly in file path
Path chartPath = Paths.get(baseDir, userInput);
Files.readAllBytes(chartPath);

// GOOD - canonicalize and validate
Path resolved = baseDir.resolve(userInput).normalize();
if (!resolved.startsWith(baseDir)) {
	throw new SecurityException("Path traversal detected: " + userInput);
}
Files.readAllBytes(resolved);
```

### Missing Input Validation

```java
// BAD - trusting external input
public void addRepo(String name, String url) {
	repos.put(name, url);
}

// GOOD - validate before use
public void addRepo(String name, String url) {
	if (name == null || name.isBlank()) {
		throw new IllegalArgumentException("Repository name must not be blank");
	}
	if (!url.startsWith("https://")) {
		throw new IllegalArgumentException("Repository URL must use HTTPS: " + url);
	}
	repos.put(name.strip(), url);
}
```

## Performance Antipatterns

### String Concatenation in Loops

```java
// BAD - creates O(n) intermediate String objects
String result = "";
for (String line : lines) {
	result += line + "\n";
}

// GOOD
StringBuilder sb = new StringBuilder();
for (String line : lines) {
	sb.append(line).append('\n');
}

// ALSO GOOD
String result = String.join("\n", lines);
```

### N+1 Data Access

```java
// BAD - one call per item inside a loop
for (Chart chart : charts) {
	List<Dependency> deps = repository.fetchDependencies(chart.getName());
	// process deps
}

// GOOD - batch fetch
Map<String, List<Dependency>> allDeps = repository.fetchAllDependencies(
		charts.stream().map(Chart::getName).toList());
for (Chart chart : charts) {
	List<Dependency> deps = allDeps.get(chart.getName());
}
```

### Unnecessary Object Creation

```java
// BAD - compiled pattern recreated every call
public boolean isValid(String name) {
	return Pattern.compile("[a-z][a-z0-9-]*").matcher(name).matches();
}

// GOOD - compile once
private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9-]*");

public boolean isValid(String name) {
	return NAME_PATTERN.matcher(name).matches();
}
```

## Testing Antipatterns

### Testing Implementation Instead of Behavior

```java
// BAD - tests internal method calls, breaks on refactoring
@Test
void testInstall() {
	service.install(chart);
	verify(service, times(1)).validateChart(chart);
	verify(service, times(1)).renderTemplates(chart);
	verify(service, times(1)).applyManifests(any());
}

// GOOD - tests observable outcome
@Test
void testInstallCreatesRelease() {
	Release release = service.install(chart);
	assertEquals("deployed", release.getStatus());
	assertEquals("my-chart", release.getChartName());
}
```

### Excessive Mocking

```java
// BAD - mocking everything, test proves nothing
@Test
void testProcess() {
	when(mockLoader.load(any())).thenReturn(mockChart);
	when(mockChart.getMetadata()).thenReturn(mockMetadata);
	when(mockMetadata.getName()).thenReturn("test");
	when(mockRenderer.render(any())).thenReturn(mockResult);
	// what are we even testing?
}

// GOOD - use real objects, mock only external boundaries
@Test
void testProcessRendersChart(@TempDir Path tempDir) {
	Path chartDir = createTestChart(tempDir, "mychart", "1.0.0");
	Chart chart = Chart.load(chartDir);
	List<String> manifests = engine.render(chart, Map.of("key", "value"));
	assertTrue(manifests.get(0).contains("mychart"));
}
```

### Non-Deterministic Tests

```java
// BAD - depends on current time, random values, or system state
@Test
void testExpiry() {
	entry.setCreatedAt(Instant.now());
	assertTrue(entry.isExpired());  // race condition
}

// GOOD - inject controlled time
@Test
void testExpiry() {
	Instant fixedTime = Instant.parse("2025-01-01T00:00:00Z");
	entry.setCreatedAt(fixedTime);
	assertTrue(entry.isExpiredAt(fixedTime.plusSeconds(3600)));
}
```

## Quick Reference

| Antipattern | Symptom | Fix |
|-------------|---------|-----|
| God class | 500+ lines, many responsibilities | Split into focused classes |
| Long method | 50+ lines | Extract methods for each logical step |
| Deep nesting | 4+ indent levels | Guard clauses, streams, extract methods |
| Magic numbers | Unexplained literals | Named constants |
| Primitive obsession | Many string/int params | Domain types, records, value objects |
| Unsafe deserialization | `readValue(input, Object.class)` | Deserialize to specific types |
| Path traversal | Unvalidated file paths | Canonicalize and check prefix |
| String concat in loops | `+=` in loop | `StringBuilder` or `String.join` |
| N+1 access | Loop with per-item fetch | Batch fetch before loop |
| Excessive mocking | Mocks returning mocks | Real objects, mock boundaries only |
