package org.alexmond.jhelm.gotemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.parse.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GoTemplateTest {

	@Test
	void testParseUnnamedTemplate() throws TemplateParseException {
		GoTemplate template = new GoTemplate();
		template.parse("Hello {{ .name }}!");
		assertNotNull(template);
	}

	@Test
	void testParseFromReader() throws TemplateParseException, IOException {
		GoTemplate template = new GoTemplate();
		StringReader reader = new StringReader("Hello {{ .name }}!");
		template.parse("test", reader);
		assertNotNull(template);
	}

	@Test
	void testExecuteMainTemplate() throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("main", "Hello {{ .name }}!");

		StringWriter writer = new StringWriter();
		Map<String, Object> data = Map.of("name", "World");
		template.execute(data, writer);

		assertEquals("Hello World!", writer.toString());
	}

	@Test
	void testHasTemplate() throws TemplateParseException {
		GoTemplate template = new GoTemplate();
		template.parse("exists", "Hello!");

		assertTrue(template.hasTemplate("exists"));
		assertFalse(template.hasTemplate("not-exists"));
	}

	@Test
	void testRootWithoutName() throws TemplateParseException {
		GoTemplate template = new GoTemplate();
		template.parse("main", "Hello!");

		Node root = template.root();
		assertNotNull(root);
	}

	@ParameterizedTest
	@CsvSource({ "main, Hello!", "secondary, Goodbye!" })
	void testRootWithName(String name, String content) throws TemplateParseException {
		GoTemplate template = new GoTemplate();
		template.parse("main", "Hello!");
		template.parse("secondary", "Goodbye!");

		Node root = template.root(name);
		assertNotNull(root);
	}

	@Test
	void testRootWithNonExistentName() throws TemplateParseException {
		GoTemplate template = new GoTemplate();
		template.parse("main", "Hello!");

		Node root = template.root("nonexistent");
		assertNull(root);
	}

	@Test
	void testParseMultipleTemplates() throws TemplateParseException {
		GoTemplate template = new GoTemplate();
		template.parse("first", "First template");
		template.parse("second", "Second template");

		assertTrue(template.hasTemplate("first"));
		assertTrue(template.hasTemplate("second"));
	}

	@Test
	void testExecuteNamedTemplate() throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("greeting", "Hello {{ .name }}!");

		StringWriter writer = new StringWriter();
		Map<String, Object> data = Map.of("name", "Alice");
		template.execute("greeting", data, writer);

		assertEquals("Hello Alice!", writer.toString());
	}

	@Test
	void testParseAndExecuteUnnamedTemplate() throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("{{ .value }}");

		StringWriter writer = new StringWriter();
		Map<String, Object> data = Map.of("value", "test");
		template.execute(data, writer);

		assertEquals("test", writer.toString());
	}

	@Test
	void testParseFromInputStream() throws IOException, TemplateParseException {
		GoTemplate template = new GoTemplate();
		String templateText = "Hello {{ .name }}!";
		InputStream inputStream = new ByteArrayInputStream(templateText.getBytes());

		template.parse("test", inputStream);
		assertTrue(template.hasTemplate("test"));
	}

	@Test
	void testExecuteNonExistentTemplateThrows() throws TemplateParseException {
		GoTemplate template = new GoTemplate();
		template.parse("main", "Hello!");

		StringWriter writer = new StringWriter();
		assertThrows(TemplateNotFoundException.class, () -> template.execute("nonexistent", new HashMap<>(), writer));
	}

	@Test
	void testExecuteNullTemplateNameThrows() throws TemplateParseException {
		GoTemplate template = new GoTemplate();
		template.parse("main", "Hello!");

		StringWriter writer = new StringWriter();
		assertThrows(TemplateNotFoundException.class, () -> template.execute(null, new HashMap<>(), writer));
	}

	@Test
	void testParseInvalidTemplateThrows() {
		GoTemplate template = new GoTemplate();
		assertThrows(TemplateParseException.class, () -> template.parse("bad", "{{ .foo | nonExistentFunc }}"));
	}

	@Test
	void testParseMultipleTemplatesWithSameName() throws TemplateParseException, IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		// First parse sets the main template name
		template.parse("main", "First");

		// Second parse with different name - should not override the main template name
		template.parse("secondary", "Second");

		// Both templates should exist
		assertTrue(template.hasTemplate("main"));
		assertTrue(template.hasTemplate("secondary"));

		// Main template name should still be "main"
		StringWriter writer = new StringWriter();
		template.execute("main", new HashMap<>(), writer);
		assertEquals("First", writer.toString());
	}

	// --- Range over maps: sorted key iteration (Bug #2 fix) ---

	@Test
	void testRangeMapSortedKeyIteration() throws TemplateParseException, IOException, TemplateException {
		// Go text/template guarantees sorted-key iteration for maps with string keys.
		// Use a HashMap (unsorted) to ensure the executor sorts keys, not the input.
		GoTemplate template = new GoTemplate();
		template.parse("main", "{{ range $k, $v := .items }}{{ $k }}={{ $v }} {{ end }}");

		Map<String, Object> items = new HashMap<>();
		items.put("cherry", 3);
		items.put("apple", 1);
		items.put("banana", 2);

		StringWriter writer = new StringWriter();
		template.execute(Map.of("items", items), writer);

		assertEquals("apple=1 banana=2 cherry=3 ", writer.toString());
	}

	@Test
	void testRangeMapSingleVariableSorted() throws TemplateParseException, IOException, TemplateException {
		// When range uses a single variable, it iterates values in sorted key order
		GoTemplate template = new GoTemplate();
		template.parse("main", "{{ range $v := .ports }}{{ $v }},{{ end }}");

		Map<String, Object> ports = new HashMap<>();
		ports.put("http", 80);
		ports.put("amqp", 5672);
		ports.put("https", 443);

		StringWriter writer = new StringWriter();
		template.execute(Map.of("ports", ports), writer);

		assertEquals("5672,80,443,", writer.toString());
	}

	// --- Range break/continue (Go 1.18) ---

	@Test
	void testRangeBreak() throws TemplateParseException, IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("main",
				"{{ range $v := .items }}{{ if eq $v \"stop\" }}{{ break }}{{ end }}[{{ $v }}]{{ end }}");

		StringWriter writer = new StringWriter();
		template.execute(Map.of("items", List.of("a", "b", "stop", "c")), writer);

		assertEquals("[a][b]", writer.toString().replaceAll("\\s+", ""));
	}

	@Test
	void testRangeContinue() throws TemplateParseException, IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("main",
				"{{ range $v := .items }}{{ if eq $v \"skip\" }}{{ continue }}{{ end }}[{{ $v }}]{{ end }}");

		StringWriter writer = new StringWriter();
		template.execute(Map.of("items", List.of("a", "skip", "b", "c")), writer);

		assertEquals("[a][b][c]", writer.toString().replaceAll("\\s+", ""));
	}

	// --- printValue: Collection rendering (Bug #3 fix) ---

	@Test
	void testPrintValueCollection() throws TemplateParseException, IOException, TemplateException {
		// Go's fmt.Sprint on a slice produces [item1 item2 item3]
		GoTemplate template = new GoTemplate();
		template.parse("main", "ranges: {{ .cidrs }}");

		List<String> cidrs = new ArrayList<>();
		cidrs.add("10.0.0.0/8");
		cidrs.add("192.168.1.0/24");

		StringWriter writer = new StringWriter();
		template.execute(Map.of("cidrs", cidrs), writer);

		assertEquals("ranges: [10.0.0.0/8 192.168.1.0/24]", writer.toString());
	}

	@Test
	void testPrintValueEmptyCollection() throws TemplateParseException, IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("main", "items: {{ .items }}");

		StringWriter writer = new StringWriter();
		template.execute(Map.of("items", new ArrayList<>()), writer);

		assertEquals("items: []", writer.toString());
	}

	@Test
	void testPrintValueMap() throws TemplateParseException, IOException, TemplateException {
		// Go's fmt.Sprint on a map produces map[key1:val1 key2:val2]
		GoTemplate template = new GoTemplate();
		template.parse("main", "labels: {{ .labels }}");

		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("app", "nginx");
		labels.put("env", "prod");

		StringWriter writer = new StringWriter();
		template.execute(Map.of("labels", labels), writer);

		assertEquals("labels: map[app:nginx env:prod]", writer.toString());
	}

	// --- Method invocation on data objects ---

	@Test
	void testMethodInvocationOnDataObject() throws TemplateParseException, IOException, TemplateException {
		// Go templates support calling methods on values: .obj.Method "arg"
		// Test using ArrayList.contains (a public method on a public class)
		GoTemplate template = new GoTemplate();
		template.parse("main", "{{ if .versions.contains \"policy/v1\" }}yes{{ else }}no{{ end }}");

		ArrayList<String> versions = new ArrayList<>(List.of("v1", "apps/v1", "policy/v1"));

		StringWriter writer = new StringWriter();
		template.execute(Map.of("versions", versions), writer);

		assertEquals("yes", writer.toString());
	}

	@Test
	void testMethodInvocationReturnsFalse() throws TemplateParseException, IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("main", "{{ if .versions.contains \"batch/v1beta1\" }}yes{{ else }}no{{ end }}");

		ArrayList<String> versions = new ArrayList<>(List.of("v1", "apps/v1"));

		StringWriter writer = new StringWriter();
		template.execute(Map.of("versions", versions), writer);

		assertEquals("no", writer.toString());
	}

	// --- Builder tests ---

	@Test
	void testBuilderNoAutoDiscovery() throws TemplateParseException, IOException, TemplateException {
		GoTemplate template = GoTemplate.builder().noAutoDiscovery().build();

		// Should have Go builtins
		assertTrue(template.getFunctions().containsKey("len"));
		assertTrue(template.getFunctions().containsKey("print"));
		assertTrue(template.getFunctions().containsKey("eq"));

		// Should work for basic templates
		template.parse("test", "Hello {{ .name }}!");
		StringWriter writer = new StringWriter();
		template.execute("test", Map.of("name", "World"), writer);
		assertEquals("Hello World!", writer.toString());
	}

	@Test
	void testBuilderWithExplicitProvider() throws TemplateParseException, IOException, TemplateException {
		FunctionProvider customProvider = new FunctionProvider() {
			@Override
			public Map<String, Function> getFunctions(GoTemplate template) {
				return Map.of("greet", (args) -> "hi " + args[0]);
			}

			@Override
			public int priority() {
				return 500;
			}

			@Override
			public String name() {
				return "TestProvider";
			}
		};

		GoTemplate template = GoTemplate.builder().noAutoDiscovery().withProvider(customProvider).build();

		assertTrue(template.getFunctions().containsKey("greet"));
		assertTrue(template.getFunctions().containsKey("len"));

		template.parse("test", "{{ greet .name }}");
		StringWriter writer = new StringWriter();
		template.execute("test", Map.of("name", "World"), writer);
		assertEquals("hi World", writer.toString());
	}

	@Test
	void testBuilderWithFunctionsOverridesProvider() {
		FunctionProvider provider = new FunctionProvider() {
			@Override
			public Map<String, Function> getFunctions(GoTemplate template) {
				return Map.of("myfn", (args) -> "from-provider");
			}
		};

		GoTemplate template = GoTemplate.builder()
			.noAutoDiscovery()
			.withProvider(provider)
			.withFunctions(Map.of("myfn", (args) -> "from-override"))
			.build();

		Function myfn = template.getFunctions().get("myfn");
		assertNotNull(myfn);
		assertEquals("from-override", myfn.invoke(new Object[] {}));
	}

	@Test
	void testBuilderProviderPriorityOrder() {
		FunctionProvider lowPriority = new FunctionProvider() {
			@Override
			public Map<String, Function> getFunctions(GoTemplate template) {
				return Map.of("shared", (args) -> "low");
			}

			@Override
			public int priority() {
				return 10;
			}
		};

		FunctionProvider highPriority = new FunctionProvider() {
			@Override
			public Map<String, Function> getFunctions(GoTemplate template) {
				return Map.of("shared", (args) -> "high");
			}

			@Override
			public int priority() {
				return 200;
			}
		};

		// Add low first, high second — high should override
		GoTemplate template = GoTemplate.builder()
			.noAutoDiscovery()
			.withProvider(lowPriority)
			.withProvider(highPriority)
			.build();

		Function shared = template.getFunctions().get("shared");
		assertEquals("high", shared.invoke(new Object[] {}));
	}

	@Test
	void testBuilderWithAutoDiscovery() {
		// Default builder has autoDiscovery=true
		GoTemplate template = GoTemplate.builder().build();

		// Should have Go builtins regardless
		assertTrue(template.getFunctions().containsKey("len"));
		assertTrue(template.getFunctions().containsKey("print"));
	}

	@Test
	void testBuilderProducesIndependentInstances() throws TemplateParseException {
		GoTemplate t1 = GoTemplate.builder().noAutoDiscovery().build();
		GoTemplate t2 = GoTemplate.builder().noAutoDiscovery().build();

		t1.parse("only-in-t1", "Hello");

		assertTrue(t1.hasTemplate("only-in-t1"));
		assertFalse(t2.hasTemplate("only-in-t1"));
	}

	@Test
	void testWithSkippedPreservesKey() throws Exception {
		// When 'with' is falsy, the preceding key text should be preserved
		GoTemplate template = new GoTemplate();
		String tmpl = "key:\n  {{- with .val }}\n  {{- print . }}\n  {{- end }}\nnext:";
		template.parse("test", tmpl);
		Map<String, Object> data = new HashMap<>();
		data.put("val", null);
		StringWriter writer = new StringWriter();
		template.execute("test", data, writer);
		String result = writer.toString();
		assertTrue(result.contains("key:"), "key: should be preserved when with is skipped");
		assertTrue(result.contains("next:"), "next: should follow after skipped with");
	}

	@Test
	void testVariableFieldChaining() throws Exception {
		// $var.field should resolve the field on the variable value
		GoTemplate template = new GoTemplate();
		String tmpl = "{{ range $k, $v := .items }}{{ $v.name }}:{{ $v.count }} {{ end }}";
		template.parse("test", tmpl);

		Map<String, Object> item1 = new HashMap<>();
		item1.put("name", "apple");
		item1.put("count", 3);
		Map<String, Object> item2 = new HashMap<>();
		item2.put("name", "banana");
		item2.put("count", 5);
		Map<String, Object> items = new LinkedHashMap<>();
		items.put("a", item1);
		items.put("b", item2);

		StringWriter writer = new StringWriter();
		template.execute("test", Map.of("items", items), writer);
		String result = writer.toString();
		assertTrue(result.contains("apple:3"), "Should resolve $v.name and $v.count: " + result);
		assertTrue(result.contains("banana:5"), "Should resolve $v.name and $v.count: " + result);
	}

	@Test
	void testVariableDeepFieldChaining() throws Exception {
		// $var.field.nested should chain multiple field accesses
		GoTemplate template = new GoTemplate();
		String tmpl = "{{ range $k, $v := .items }}{{ $v.meta.label }} {{ end }}";
		template.parse("test", tmpl);

		Map<String, Object> meta = new HashMap<>();
		meta.put("label", "important");
		Map<String, Object> item = new HashMap<>();
		item.put("meta", meta);

		StringWriter writer = new StringWriter();
		template.execute("test", Map.of("items", Map.of("x", item)), writer);
		String result = writer.toString();
		assertTrue(result.contains("important"), "Should resolve $v.meta.label: " + result);
	}

}
