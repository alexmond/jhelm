---
name: "tdd-guide"
description: "Test-driven development guide for writing JUnit 5 tests, analyzing coverage gaps, and guiding red-green-refactor workflows. Use when the user asks to write tests, improve test coverage, practice TDD, generate test stubs, or mentions JUnit/testing."
---

# TDD Guide

Test-driven development skill for generating tests, analyzing coverage, and guiding red-green-refactor workflows. Adapted from [alirezarezvani/claude-skills](https://github.com/alirezarezvani/claude-skills) for JUnit 5 and the jhelm project.

---

## Workflows

### Generate Tests from Code

1. Provide source code (Java class or method)
2. Target framework: **JUnit 5** (`org.junit.jupiter.api`)
3. Follow jhelm conventions:
   - Use `Assertions` (not AssertJ)
   - Use `@TempDir` for temporary files
   - Use `@ParameterizedTest` with `@CsvSource` or `@MethodSource` to avoid duplication
   - Prefer real test data over Mockito mocks
   - Mockito acceptable only for HTTP (`CloseableHttpClient`) and Kubernetes (`KubeService`)
4. **Validation:** Tests compile and cover happy path, error cases, edge cases

### Analyze Coverage Gaps

1. Run JaCoCo: `./mvnw test` (generates `target/site/jacoco/jacoco.xml`)
2. Use `/jacoco` skill to analyze coverage by module
3. Prioritize gaps:
   - **P0:** 0% covered classes with many lines (high impact)
   - **P1:** Partially covered classes with uncovered branches
   - **P2:** Low-risk utility classes
4. Generate tests for P0 items first
5. **Target:** 80% minimum coverage per module

### TDD New Feature

1. Write failing test first (RED)
2. Run: `./mvnw test -Dtest=NewFeatureTest -pl <module>`
3. Implement minimal code to pass (GREEN)
4. Run test again to confirm pass
5. Refactor while keeping tests green (REFACTOR)
6. Run `./mvnw validate` to verify formatting + PMD + checkstyle

---

## JUnit 5 Patterns for jhelm

### Basic Test

```java
@Test
void testChartLoadingFromDirectory() throws Exception {
	Chart chart = ChartLoader.load(Path.of("src/test/resources/test-charts/minimal"));
	assertEquals("minimal", chart.getMetadata().getName());
	assertNotNull(chart.getValues());
}
```

### Parameterized Test (avoid duplication)

```java
@ParameterizedTest
@CsvSource(delimiter = '|', value = {
	"upper     | hello | HELLO",
	"lower     | HELLO | hello",
	"title     | hello world | Hello World"
})
void testStringFunctions(String func, String input, String expected)
		throws IOException, TemplateException {
	assertEquals(expected, exec("{{ " + func + " \"" + input + "\" }}"));
}
```

### Template Engine Test

```java
@Test
void testTemplateRendering() throws IOException, TemplateException {
	GoTemplate template = new GoTemplate();
	template.parse("test", "Hello {{ .name }}!");
	StringWriter writer = new StringWriter();
	template.execute("test", Map.of("name", "World"), writer);
	assertEquals("Hello World!", writer.toString());
}
```

### Mocking External Dependencies

```java
@Test
void testPullChartWithAuth() throws Exception {
	CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
	when(mockClient.execute(any())).thenReturn(httpAnswer(200, chartBytes));

	RepoManager rm = new RepoManager();
	rm.setHttpClient(mockClient);
	rm.pull("myrepo", "mychart", "1.0.0", tempDir);

	assertTrue(tempDir.resolve("mychart-1.0.0.tgz").toFile().exists());
}
```

### Using @TempDir

```java
@Test
void testChartPackaging(@TempDir Path tempDir) throws Exception {
	Path chartDir = tempDir.resolve("mychart");
	Files.createDirectories(chartDir);
	Files.writeString(chartDir.resolve("Chart.yaml"), """
			apiVersion: v2
			name: mychart
			version: 1.0.0
			""");
	File tgz = ChartPackager.pack(chartDir.toFile(), tempDir.toFile());
	assertTrue(tgz.exists());
}
```

---

## Red-Green-Refactor Cycle

### RED — Write Failing Test

```java
@Test
void testNewFeature() {
	// This test should FAIL initially
	MyService service = new MyService();
	String result = service.newMethod("input");
	assertEquals("expected output", result);
}
```

Run: `./mvnw test -Dtest=MyServiceTest#testNewFeature -pl jhelm-core`
Expected: **FAIL** (method doesn't exist yet)

### GREEN — Minimal Implementation

```java
public String newMethod(String input) {
	return "expected output"; // Minimal to pass
}
```

Run: `./mvnw test -Dtest=MyServiceTest#testNewFeature -pl jhelm-core`
Expected: **PASS**

### REFACTOR — Clean Up

- Extract constants, reduce duplication
- Run full test suite: `./mvnw test -pl jhelm-core`
- Run validation: `./mvnw validate -pl jhelm-core`

---

## Coverage Analysis Strategy

When increasing coverage for a module:

1. **Start with modules closest to threshold** (small gap = quick win)
2. **Focus on 0% coverage classes** with many lines (high impact)
3. **Use the `/jacoco` skill** to identify specific uncovered lines
4. **Incremental approach:** 60% → 70% → 80%
5. **Check what's already tested** before writing duplicate tests

### jhelm Module Coverage Targets

| Module | Target | Notes |
|--------|--------|-------|
| jhelm-core | 80% | Action classes, Engine, RepoManager |
| jhelm-app | 80% | CLI commands via Picocli CommandLine.execute() |
| jhelm-gotemplate | 80% | Template functions, Lexer, Parser |
| jhelm-kube | 80% | Mock KubeService for k8s operations |

---

## Mutation Testing (Advanced)

For critical paths, use PIT (PiTest) to verify test quality beyond coverage:

```bash
./mvnw org.pitest:pitest-maven:mutationCoverage -pl jhelm-core
```

- **100% line coverage != good tests** — coverage tells you code was executed, not verified
- **Target 85%+ mutation score** on critical modules (auth, chart extraction, values merging)

---

## Bounded Autonomy Rules

### Stop and Ask When
- Ambiguous requirements or unclear acceptance criteria
- Test count exceeds 50 for a single class
- Security-sensitive logic (auth, crypto, chart extraction)
- External dependencies with undocumented behavior

### Continue Autonomously When
- Clear spec with well-defined inputs/outputs
- CRUD operations with well-defined models
- Pure functions (deterministic input/output)
- Existing test patterns to follow in the codebase
