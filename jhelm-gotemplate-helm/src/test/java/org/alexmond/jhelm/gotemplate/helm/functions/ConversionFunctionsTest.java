package org.alexmond.jhelm.gotemplate.helm.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConversionFunctionsTest {

	private void execute(String name, String text, Object data, StringWriter writer)
			throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse(name, text);
		template.execute(name, data, writer);
	}

	private String exec(String template) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		execute("test", template, new HashMap<>(), writer);
		return writer.toString();
	}

	private String execWithData(String template, Map<String, Object> data) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		execute("test", template, data, writer);
		return writer.toString();
	}

	// JSON parsing tests

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ $data := fromJson \"{\\\"name\\\":\\\"John\\\"}\" }}{{ $data.name }}             | John",
					"{{ $data := mustFromJson \"{\\\"age\\\":30}\" }}{{ $data.age }}                     | 30",
					"{{ $arr := fromJsonArray \"[1,2,3]\" }}{{ len $arr }}                                | 3",
					"{{ $arr := mustFromJsonArray \"[\\\"a\\\",\\\"b\\\"]\" }}{{ len $arr }}              | 2" })
	void testJsonParsing(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	// JSON serialization tests

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "toJson       | name  | Alice | Alice", "mustToJson   | value | 42    | 42",
			"toRawJson    | text  | hello | hello", "mustToRawJson| num   | 123   | 123" })
	void testJsonSerialization(String func, String key, Object value, String expectedContains)
			throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put(key, (value instanceof String s && s.matches("\\d+")) ? Integer.parseInt(s) : value);
		String result = execWithData("{{ " + func + " ." + key + " }}", data);
		assertTrue(result.contains(expectedContains.toString()));
	}

	@Test
	void testToPrettyJson() throws IOException, TemplateException {
		Map<String, Object> data = Map.of("obj", Map.of("key", "value"));
		assertFalse(execWithData("{{ toPrettyJson .obj }}", new HashMap<>(data)).isEmpty());
	}

	@Test
	void testMustToPrettyJson() throws IOException, TemplateException {
		Map<String, Object> data = Map.of("obj", Map.of("a", 1, "b", 2));
		assertFalse(execWithData("{{ mustToPrettyJson .obj }}", new HashMap<>(data)).isEmpty());
	}

	// YAML parsing tests

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ $data := fromYaml \"name: Bob\" }}{{ $data.name }}                  | Bob",
					"{{ $data := mustFromYaml \"count: 5\" }}{{ $data.count }}              | 5",
					"{{ $arr := fromYamlArray \"- a\\n- b\\n- c\" }}{{ len $arr }}          | 3",
					"{{ $arr := mustFromYamlArray \"- x\\n- y\" }}{{ len $arr }}            | 2" })
	void testYamlParsing(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	// YAML serialization tests

	@ParameterizedTest
	@CsvSource({ "toYaml, name", "mustToYaml, name" })
	void testYamlSerialization(String func, String expectedContains) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("obj", Map.of("name", "test"));
		String result = execWithData("{{ " + func + " .obj }}", data);
		assertTrue(result.contains(expectedContains));
	}

	@ParameterizedTest
	@CsvSource({ "toYaml", "mustToYaml" })
	void testYamlNumericStringsQuoted(String func) throws IOException, TemplateException {
		Map<String, Object> annotations = new HashMap<>();
		annotations.put("helm.sh/hook-weight", "-5");
		annotations.put("helm.sh/hook", "pre-install");
		annotations.put("port", "8080");

		Map<String, Object> data = new HashMap<>();
		data.put("obj", annotations);

		String result = execWithData("{{ " + func + " .obj }}", data);
		// Numeric-looking strings must be quoted to match Go yaml.Marshal
		assertTrue(result.contains("\"-5\"") || result.contains("'-5'"),
				"Numeric string '-5' should be quoted: " + result);
		assertTrue(result.contains("\"8080\"") || result.contains("'8080'"),
				"Numeric string '8080' should be quoted: " + result);
		// Non-numeric strings should be plain (minimized)
		assertTrue(result.contains("pre-install") && !result.contains("\"pre-install\""),
				"Non-numeric string should not be quoted: " + result);
	}

	@ParameterizedTest
	@CsvSource({ "toYaml", "mustToYaml" })
	void testYamlNullPreservation(String func) throws IOException, TemplateException {
		// Go yaml.Marshal preserves nil map entries as "null" (no omitempty on maps)
		Map<String, Object> obj = new HashMap<>();
		obj.put("name", "myapp");
		obj.put("replicas", 3);
		obj.put("revisionHistoryLimit", null);
		obj.put("dnsPolicy", null);

		Map<String, Object> data = new HashMap<>();
		data.put("obj", obj);

		String result = execWithData("{{ " + func + " .obj }}", data);
		assertTrue(result.contains("name: myapp"), "non-null string field should be present");
		assertTrue(result.contains("replicas: 3"), "non-null integer field should be present");
		assertTrue(result.contains("revisionHistoryLimit: null"), "null field should be rendered as null");
		assertTrue(result.contains("dnsPolicy: null"), "null field should be rendered as null");
	}

	// --- Direct function invocation tests for edge cases ---

	private Map<String, Function> functions() {
		return ConversionFunctions.getFunctions();
	}

	@Test
	void testToYamlNullAndEmpty() {
		Function toYaml = functions().get("toYaml");
		assertEquals("", toYaml.invoke(new Object[] {}));
		assertEquals("", toYaml.invoke(new Object[] { null }));
	}

	@Test
	void testToJsonNullReturnsNullString() {
		Function toJson = functions().get("toJson");
		assertEquals("null", toJson.invoke(new Object[] {}));
		assertEquals("null", toJson.invoke(new Object[] { null }));
	}

	@Test
	void testToJsonWholeNumberDoubleRenderedAsInteger() {
		Function toJson = functions().get("toJson");
		// Go's json.Marshal normalizes float64(1.0) to 1
		Map<String, Object> data = new java.util.LinkedHashMap<>();
		data.put("traceSampling", 1.0);
		data.put("rate", 100.0);
		data.put("ratio", 0.5);
		String json = (String) toJson.invoke(new Object[] { data });
		assertTrue(json.contains("\"traceSampling\":1,") || json.contains("\"traceSampling\":1}"),
				"1.0 should render as 1: " + json);
		assertTrue(json.contains("\"rate\":100"), "100.0 should render as 100: " + json);
		assertTrue(json.contains("\"ratio\":0.5"), "0.5 should remain as 0.5: " + json);
	}

	@Test
	void testToRawJsonNullReturnsNullString() {
		Function toRawJson = functions().get("toRawJson");
		assertEquals("null", toRawJson.invoke(new Object[] {}));
		assertEquals("null", toRawJson.invoke(new Object[] { null }));
	}

	@Test
	void testToPrettyJsonNullReturnsNullString() {
		Function fn = functions().get("toPrettyJson");
		assertEquals("null", fn.invoke(new Object[] {}));
		assertEquals("null", fn.invoke(new Object[] { null }));
	}

	@Test
	void testFromYamlNullAndEmpty() {
		Function fromYaml = functions().get("fromYaml");
		assertEquals(Map.of(), fromYaml.invoke(new Object[] {}));
		assertEquals(Map.of(), fromYaml.invoke(new Object[] { null }));
		assertEquals(Map.of(), fromYaml.invoke(new Object[] { "  " }));
	}

	@Test
	void testFromJsonNullAndEmpty() {
		Function fromJson = functions().get("fromJson");
		assertEquals(Map.of(), fromJson.invoke(new Object[] {}));
		assertEquals(Map.of(), fromJson.invoke(new Object[] { null }));
		assertEquals(Map.of(), fromJson.invoke(new Object[] { "  " }));
		assertEquals(Map.of(), fromJson.invoke(new Object[] { "null" }));
	}

	@Test
	void testFromYamlArrayNullAndEmpty() {
		Function fn = functions().get("fromYamlArray");
		assertEquals(Collections.emptyList(), fn.invoke(new Object[] {}));
		assertEquals(Collections.emptyList(), fn.invoke(new Object[] { null }));
		assertEquals(Collections.emptyList(), fn.invoke(new Object[] { "  " }));
	}

	@Test
	void testFromJsonArrayNullAndEmpty() {
		Function fn = functions().get("fromJsonArray");
		assertEquals(Collections.emptyList(), fn.invoke(new Object[] {}));
		assertEquals(Collections.emptyList(), fn.invoke(new Object[] { null }));
		assertEquals(Collections.emptyList(), fn.invoke(new Object[] { "  " }));
		assertEquals(Collections.emptyList(), fn.invoke(new Object[] { "null" }));
	}

	// must* variants throw on null/empty

	@Test
	void testMustToYamlThrowsOnNull() {
		Function fn = functions().get("mustToYaml");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
	}

	@Test
	void testMustToJsonThrowsOnNull() {
		Function fn = functions().get("mustToJson");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
	}

	@Test
	void testMustToPrettyJsonThrowsOnNull() {
		Function fn = functions().get("mustToPrettyJson");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
	}

	@Test
	void testMustToRawJsonThrowsOnNull() {
		Function fn = functions().get("mustToRawJson");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
	}

	@Test
	void testMustFromYamlThrowsOnNull() {
		Function fn = functions().get("mustFromYaml");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { "  " }));
	}

	@Test
	void testMustFromJsonThrowsOnNull() {
		Function fn = functions().get("mustFromJson");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { "  " }));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { "null" }));
	}

	@Test
	void testMustFromYamlArrayThrowsOnNull() {
		Function fn = functions().get("mustFromYamlArray");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { "  " }));
	}

	@Test
	void testMustFromJsonArrayThrowsOnNull() {
		Function fn = functions().get("mustFromJsonArray");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { "  " }));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { "null" }));
	}

	@Test
	void testGetFunctionsReturnsAllExpected() {
		Map<String, Function> fns = functions();
		List<String> expected = List.of("toYaml", "mustToYaml", "fromYaml", "mustFromYaml", "fromYamlArray",
				"mustFromYamlArray", "toJson", "mustToJson", "toPrettyJson", "mustToPrettyJson", "toRawJson",
				"mustToRawJson", "fromJson", "mustFromJson", "fromJsonArray", "mustFromJsonArray", "toToml",
				"mustToToml", "fromToml", "mustFromToml");
		for (String name : expected) {
			assertTrue(fns.containsKey(name), "missing function: " + name);
		}
		assertEquals(expected.size(), fns.size());
	}

	@Test
	void testFromJsonReturnsMap() {
		Function fn = functions().get("fromJson");
		Object result = fn.invoke(new Object[] { "{\"a\":1}" });
		assertInstanceOf(Map.class, result);
	}

	@Test
	void testFromJsonArrayReturnsList() {
		Function fn = functions().get("fromJsonArray");
		Object result = fn.invoke(new Object[] { "[1,2,3]" });
		assertInstanceOf(List.class, result);
		assertEquals(3, ((List<?>) result).size());
	}

	// --- toYaml with regex values (backslash handling) ---

	@Test
	void testToYamlRegexValueUnquoted() {
		// Go yaml.Marshal outputs regex values as plain (unquoted) YAML
		Function toYaml = functions().get("toYaml");
		Map<String, String> data = Map.of("regex", "\\d+");
		String result = (String) toYaml.invoke(new Object[] { data });
		// Should be: regex: \d+ (not: regex: "\\d+")
		assertEquals("regex: \\d+", result);
	}

	@Test
	void testToYamlComplexRegexUnquoted() {
		// Prometheus pattern with complex regex — should NOT be double-quoted
		Function toYaml = functions().get("toYaml");
		String regex = "(\\d+);(([A-Fa-f0-9]{1,4}::?){1,7}[A-Fa-f0-9]{1,4})";
		Map<String, String> data = Map.of("regex", regex);
		String result = (String) toYaml.invoke(new Object[] { data });
		assertEquals("regex: " + regex, result);
	}

	@Test
	void testToYamlPreservesQuotesWhenNeeded() {
		// Values starting with flow indicators must stay quoted
		Function toYaml = functions().get("toYaml");
		Map<String, String> data = Map.of("val", "{flow}");
		String result = (String) toYaml.invoke(new Object[] { data });
		assertTrue(result.contains("\""), "values starting with { must stay quoted");
	}

	@Test
	void testToYamlPreservesBooleanQuotes() {
		// Boolean-like strings must stay quoted
		Function toYaml = functions().get("toYaml");
		Map<String, String> data = Map.of("val", "true");
		String result = (String) toYaml.invoke(new Object[] { data });
		assertTrue(result.contains("\"true\""), "boolean-like strings must stay quoted");
	}

	// --- TOML functions ---

	@Test
	void testToToml() {
		Function fn = functions().get("toToml");
		Map<String, Object> data = new HashMap<>();
		data.put("name", "myapp");
		data.put("port", 8080);
		String result = (String) fn.invoke(new Object[] { data });
		assertTrue(result.contains("myapp"), "toToml should serialize string: " + result);
		assertTrue(result.contains("port = 8080"), "toToml should serialize integer: " + result);
	}

	@Test
	void testToTomlNullReturnsEmpty() {
		Function fn = functions().get("toToml");
		assertEquals("", fn.invoke(new Object[] { null }));
		assertEquals("", fn.invoke(new Object[] {}));
	}

	@Test
	void testMustToTomlThrowsOnNull() {
		Function fn = functions().get("mustToToml");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testFromToml() {
		Function fn = functions().get("fromToml");
		String toml = "name = \"myapp\"\nport = 8080\n";
		Map<String, Object> result = (Map<String, Object>) fn.invoke(new Object[] { toml });
		assertEquals("myapp", result.get("name"));
		assertEquals(8080, ((Number) result.get("port")).intValue());
	}

	@Test
	void testFromTomlNullReturnsEmptyMap() {
		Function fn = functions().get("fromToml");
		assertEquals(Map.of(), fn.invoke(new Object[] { null }));
		assertEquals(Map.of(), fn.invoke(new Object[] { "" }));
	}

	@Test
	void testMustFromTomlThrowsOnNull() {
		Function fn = functions().get("mustFromToml");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { "  " }));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testTomlRoundTrip() {
		Function toToml = functions().get("toToml");
		Function fromToml = functions().get("fromToml");
		Map<String, Object> original = new HashMap<>();
		original.put("key", "value");
		original.put("count", 42);
		String toml = (String) toToml.invoke(new Object[] { original });
		Map<String, Object> parsed = (Map<String, Object>) fromToml.invoke(new Object[] { toml });
		assertEquals("value", parsed.get("key"));
		assertEquals(42, ((Number) parsed.get("count")).intValue());
	}

}
