package org.alexmond.jhelm.gotemplate.helm.functions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.gotmpl4j.GoTemplate;
import org.alexmond.gotmpl4j.TemplateException;
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

	@Test
	void testToYamlSortsKeysLikeHelm() throws IOException, TemplateException {
		// Helm's toYaml marshals via sigs.k8s.io/yaml (-> JSON, which sorts map keys), so
		// jhelm must sort too — otherwise toYaml output that gets hashed (resource-name
		// suffixes, checksum/* annotations) diverges from Helm byte-for-byte (#237).
		Map<String, Object> obj = new java.util.LinkedHashMap<>();
		obj.put("zebra", 1);
		obj.put("alpha", 2);
		obj.put("mike", 3);
		Map<String, Object> data = new HashMap<>();
		data.put("obj", obj);
		String result = execWithData("{{ toYaml .obj }}", data).trim();
		assertEquals("alpha: 2\nmike: 3\nzebra: 1", result, "toYaml must emit keys in sorted order");
	}

	@Test
	void testToYamlQuoteStyleMatchesGoYaml() {
		// Go's yaml.Marshal (Helm's toYaml) single-quotes non-plain scalars (special
		// chars,
		// flow-indicator starts) but double-quotes empty/numeric/keyword strings.
		// Verified
		// against `helm template`.
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("withColon", "key: value");
		data.put("withHash", "a # b");
		data.put("flow", "{{ x }}");
		data.put("numStr", "123");
		data.put("emptyStr", "");
		data.put("tilde", "~");
		String result = (String) functions().get("toYaml").invoke(new Object[] { data });
		assertTrue(result.contains("withColon: 'key: value'"), "special-char string is single-quoted: " + result);
		assertTrue(result.contains("withHash: 'a # b'"), "hash string is single-quoted: " + result);
		assertTrue(result.contains("flow: '{{ x }}'"), "flow-indicator string is single-quoted: " + result);
		assertTrue(result.contains("numStr: \"123\""), "numeric string stays double-quoted: " + result);
		assertTrue(result.contains("emptyStr: \"\""), "empty string stays double-quoted: " + result);
		assertTrue(result.contains("tilde: \"~\""), "null keyword stays double-quoted: " + result);
	}

	@Test
	void testToYamlQuotesTrailingColonStrings() {
		// A value ending in ':' (e.g. a Prometheus recording-rule name) would otherwise
		// be
		// read back as a mapping key and corrupt the document — Go's yaml.Marshal quotes
		// it.
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("record", "node_namespace_pod:kube_pod_info:");
		String result = (String) functions().get("toYaml").invoke(new Object[] { data });
		assertTrue(
				result.contains("'node_namespace_pod:kube_pod_info:'")
						|| result.contains("\"node_namespace_pod:kube_pod_info:\""),
				"trailing-colon value must be quoted: " + result);
		// Sanity: the result must round-trip as valid YAML.
		Object back = functions().get("fromYaml").invoke(new Object[] { result });
		assertInstanceOf(Map.class, back);
		assertEquals("node_namespace_pod:kube_pod_info:", ((Map<?, ?>) back).get("record"));
	}

	@Test
	void testToYamlRendersWholeFloatsAsIntegers() {
		// Helm marshals via JSON, where float64(1.0) -> "1". jhelm must match (whole
		// floats
		// lose the ".0") so hashed toYaml agrees; fractional floats are unchanged.
		Function toYaml = functions().get("toYaml");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("whole", 1.0);
		data.put("frac", 0.5);
		String result = (String) toYaml.invoke(new Object[] { data });
		assertTrue(result.contains("whole: 1\n") || result.endsWith("whole: 1"),
				"whole-number float must render as int: " + result);
		assertTrue(result.contains("frac: 0.5"), "fractional float must be unchanged: " + result);
	}

	@Test
	void testToYamlSortsNestedKeys() throws IOException, TemplateException {
		Map<String, Object> inner = new java.util.LinkedHashMap<>();
		inner.put("bb", 1);
		inner.put("aa", 2);
		Map<String, Object> obj = new java.util.LinkedHashMap<>();
		obj.put("outer", inner);
		Map<String, Object> data = new HashMap<>();
		data.put("obj", obj);
		String result = execWithData("{{ toYaml .obj }}", data).trim();
		assertEquals("outer:\n  aa: 2\n  bb: 1", result, "toYaml must sort nested map keys too");
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

	@Test
	void testToYamlPrettyIndentsSequenceItems() throws IOException, TemplateException {
		// Helm's toYamlPretty indents block sequence items under their key
		// (" - a: 1"), unlike toYaml which aligns the dash with the key ("- a: 1").
		Map<String, Object> obj = new LinkedHashMap<>();
		obj.put("list", List.of(Map.of("a", 1)));
		obj.put("map", Map.of("k", "v"));
		Map<String, Object> data = new HashMap<>();
		data.put("obj", obj);

		String pretty = execWithData("{{ toYamlPretty .obj }}", data);
		String plain = execWithData("{{ toYaml .obj }}", data);

		assertTrue(pretty.contains("list:\n  - a: 1"), "pretty should indent sequence items: " + pretty);
		assertTrue(plain.contains("list:\n- a: 1"), "plain toYaml aligns dash with key: " + plain);
		assertTrue(pretty.contains("map:\n  k: v"), "mapping indentation unchanged: " + pretty);
	}

	@Test
	void testToYamlPrettyNullAndEmpty() {
		Function toYamlPretty = functions().get("toYamlPretty");
		assertEquals("", toYamlPretty.invoke(new Object[] {}));
		// Helm's toYamlPretty(nil) marshals to the literal "null" (yaml.Marshal(nil)).
		assertEquals("null", toYamlPretty.invoke(new Object[] { null }));
	}

	@ParameterizedTest
	@CsvSource({ "toYaml", "mustToYaml" })
	void testYamlSequenceItemWithEmbeddedQuotesIsPlain(String func) throws IOException, TemplateException {
		// #312: a list element containing double quotes (e.g. a shell command with a
		// nested {{ include "x" . }}) was emitted as a double-quoted scalar with \"
		// escapes. Go's yaml.Marshal emits it plain, and the backslashes break a
		// subsequent tpl(toYaml ...) re-parse. Sequence items must be normalised to
		// plain style just like mapping values are.
		Map<String, Object> data = new HashMap<>();
		data.put("obj", Map.of("command", List.of("sh", "-c", "until nc {{ include \"etcdUrl\" . }} 80; done")));

		String result = execWithData("{{ " + func + " .obj }}", data);

		assertTrue(result.contains("- until nc {{ include \"etcdUrl\" . }} 80; done"),
				"sequence item should be a plain scalar, got: " + result);
		assertFalse(result.contains("\\\""), "sequence item must not contain backslash-escaped quotes: " + result);
		// The whole point: the emitted YAML must itself be re-parseable as a template
		// (this is what tpl (toYaml ...) does).
		assertDoesNotThrow(() -> new GoTemplate().parse("inline", result),
				"toYaml output must be parseable by tpl: " + result);
	}

	@ParameterizedTest
	@CsvSource({ "toYaml", "mustToYaml" })
	void testYamlNonPlainValueWithQuotesIsSingleQuoted(String func) throws IOException, TemplateException {
		// #327: a value that cannot be a plain scalar (starts with the flow indicator
		// "{{") but contains double quotes was emitted double-quoted with \" escapes,
		// breaking a subsequent tpl(toYaml ...). Go emits it single-quoted, e.g.
		// server_address: '{{ include "loki.x" . }}'.
		Map<String, Object> data = new HashMap<>();
		data.put("obj", Map.of("server_address", "{{ include \"loki.indexGatewayAddress\" . }}"));

		String result = execWithData("{{ " + func + " .obj }}", data);

		assertTrue(result.contains("server_address: '{{ include \"loki.indexGatewayAddress\" . }}'"),
				"non-plain value with quotes should be single-quoted, got: " + result);
		assertFalse(result.contains("\\\""), "must not contain backslash-escaped quotes: " + result);
		assertDoesNotThrow(() -> new GoTemplate().parse("inline", result),
				"toYaml output must be parseable by tpl: " + result);
	}

	// --- Direct function invocation tests for edge cases ---

	private Map<String, Function> functions() {
		return ConversionFunctions.getFunctions();
	}

	@Test
	void testToYamlNullAndEmpty() {
		Function toYaml = functions().get("toYaml");
		assertEquals("", toYaml.invoke(new Object[] {}));
		// Helm's toYaml(nil) marshals to the literal "null" (yaml.Marshal(nil) ->
		// "null\n").
		assertEquals("null", toYaml.invoke(new Object[] { null }));
	}

	@Test
	void testToYamlRootScalarHasNoDocMarker() {
		// Jackson emits an inline document-start marker for a root scalar ("" -> `---
		// ""`).
		// Helm's toYaml marshals a bare scalar, so the marker must be stripped.
		Function toYaml = functions().get("toYaml");
		assertEquals("\"\"", toYaml.invoke(new Object[] { "" }));
		assertEquals("5", toYaml.invoke(new Object[] { 5 }));
		assertEquals("hello", toYaml.invoke(new Object[] { "hello" }));
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
		Map<String, Object> data = new LinkedHashMap<>();
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
	void testToJsonSortsKeysAlphabetically() {
		Function toJson = functions().get("toJson");
		// Go's json.Marshal sorts map keys alphabetically
		Map<String, Object> data = new HashMap<>();
		data.put("name", "dags-folder");
		data.put("classpath", "airflow.LocalDagBundle");
		data.put("kwargs", Map.of());
		String json = (String) toJson.invoke(new Object[] { data });
		// Keys should be in alphabetical order: classpath, kwargs, name
		assertTrue(json.indexOf("classpath") < json.indexOf("kwargs"), "classpath should come before kwargs: " + json);
		assertTrue(json.indexOf("kwargs") < json.indexOf("name"), "kwargs should come before name: " + json);
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
	@SuppressWarnings("unchecked")
	void testFromYamlDuplicateKeyTakesLast() {
		// Helm parses via sigs.k8s.io/yaml where a repeated key silently takes the last
		// value (bitnami/grafana-mimir's mimir.yaml repeats `ingester:`). SnakeYAML
		// Engine
		// rejects duplicates by default; jhelm must allow them and keep the last.
		Function fromYaml = functions().get("fromYaml");
		Map<String, Object> result = (Map<String, Object>) fromYaml
			.invoke(new Object[] { "a:\n  first: 1\na:\n  second: 2\n" });
		Map<String, Object> a = (Map<String, Object>) result.get("a");
		assertEquals(2, ((Number) a.get("second")).intValue(), "last duplicate key value wins");
		assertFalse(a.containsKey("first"), "earlier duplicate key is dropped, not merged");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testFromYamlParseErrorReturnsErrorKey() {
		// Helm's fromYaml surfaces a parse failure under an "Error" key rather than
		// silently returning an empty map (which would hide the failure).
		Function fromYaml = functions().get("fromYaml");
		Map<String, Object> result = (Map<String, Object>) fromYaml.invoke(new Object[] { "a: b: c" });
		assertNotNull(result.get("Error"), "parse failure must surface under an Error key");
	}

	@Test
	void testFromYamlArrayParseErrorReturnsError() {
		// Helm's fromYamlArray returns [err] on a parse failure, not an empty list.
		Function fromYamlArray = functions().get("fromYamlArray");
		List<?> result = (List<?>) fromYamlArray.invoke(new Object[] { "- a: b: c" });
		assertEquals(1, result.size(), "parse failure must surface as a single-element error list");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testFromYamlOctalNumber() {
		// YAML 1.1 bare-octal: 0660 → integer 432
		Function fromYaml = functions().get("fromYaml");
		Map<String, Object> result = (Map<String, Object>) fromYaml.invoke(new Object[] { "mode: 0660" });
		assertEquals(432, ((Number) result.get("mode")).intValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testFromYamlOctalPermissions() {
		// Common file permission octals
		Function fromYaml = functions().get("fromYaml");
		Map<String, Object> result = (Map<String, Object>) fromYaml.invoke(new Object[] { "perms: 0755" });
		assertEquals(493, ((Number) result.get("perms")).intValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testFromYamlQuotedOctalStaysString() {
		// Quoted values must remain strings
		Function fromYaml = functions().get("fromYaml");
		Map<String, Object> result = (Map<String, Object>) fromYaml.invoke(new Object[] { "mode: \"0660\"" });
		assertEquals("0660", result.get("mode"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testFromYamlYaml12Octal() {
		// YAML 1.2 0o prefix must still work
		Function fromYaml = functions().get("fromYaml");
		Map<String, Object> result = (Map<String, Object>) fromYaml.invoke(new Object[] { "mode: 0o660" });
		assertEquals(432, ((Number) result.get("mode")).intValue());
	}

	@Test
	void testToJsonPreservesOctalAsInteger() throws IOException, TemplateException {
		// fromYaml | toJson pipeline should output the integer, not the string
		String result = exec("{{ $d := fromYaml \"mode: 0660\" }}{{ toJson $d }}");
		assertEquals("{\"mode\":432}", result);
	}

	@Test
	void testFromYamlBlockScalarAtEof() {
		// Reproduces #215: include returns YAML ending with block scalar indicator |-
		// at EOF with no trailing newline, causing Jackson to fail
		Function fromYaml = functions().get("fromYaml");
		String yaml = "data:\n  users.acl: |-";
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) fromYaml.invoke(new Object[] { yaml });
		assertTrue(result.containsKey("data"), "Should parse YAML with block scalar at EOF: " + result);
	}

	@Test
	void testFromYamlTrailingContent() {
		// Go's yaml.Unmarshal reads only the first document — trailing content ignored
		Function fromYaml = functions().get("fromYaml");
		String yaml = "name: test\n---\nother: doc\n";
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) fromYaml.invoke(new Object[] { yaml });
		assertEquals("test", result.get("name"));
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
	void testMustToYamlNullAndEmpty() {
		Function fn = functions().get("mustToYaml");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
		// mustToYaml(nil) does not error in Helm; yaml.Marshal(nil) succeeds with "null".
		assertEquals("null", fn.invoke(new Object[] { null }));
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
	void testMustFromJsonShapes() {
		Function fn = functions().get("mustFromJson");
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] {}));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { null }));
		assertThrows(RuntimeException.class, () -> fn.invoke(new Object[] { "  " }));
		// Helm's mustFromJson decodes into interface{}: "null" -> nil, a JSON array -> a
		// list, a JSON object -> a map (verified against `helm template`).
		assertNull(fn.invoke(new Object[] { "null" }));
		Object arr = fn.invoke(new Object[] { "[1,2,3]" });
		assertInstanceOf(List.class, arr);
		assertEquals(3, ((List<?>) arr).size());
		assertInstanceOf(Map.class, fn.invoke(new Object[] { "{\"a\":1}" }));
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
		List<String> expected = List.of("toYaml", "toYamlPretty", "mustToYaml", "fromYaml", "mustFromYaml",
				"fromYamlArray", "mustFromYamlArray", "toJson", "mustToJson", "toPrettyJson", "mustToPrettyJson",
				"toRawJson", "mustToRawJson", "fromJson", "mustFromJson", "fromJsonArray", "mustFromJsonArray",
				"toToml", "mustToToml", "fromToml", "mustFromToml");
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

	@Test
	void testToYamlSortsKeysNotInsertionOrder() {
		// Helm's toYaml marshals via sigs.k8s.io/yaml (-> JSON), which sorts map keys —
		// it does NOT preserve insertion order. jhelm must match so hashed toYaml agrees.
		Function toYaml = functions().get("toYaml");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("zebra", "last");
		data.put("alpha", "first");
		data.put("middle", "mid");
		String result = (String) toYaml.invoke(new Object[] { data });
		int zebraPos = result.indexOf("zebra");
		int alphaPos = result.indexOf("alpha");
		int middlePos = result.indexOf("middle");
		assertTrue(alphaPos < middlePos, "alpha should come before middle (sorted): " + result);
		assertTrue(middlePos < zebraPos, "middle should come before zebra (sorted): " + result);
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
		// Values starting with flow indicators must stay quoted. Go's yaml.Marshal uses
		// single-quote style for these (not double), so jhelm emits '{flow}'.
		Function toYaml = functions().get("toYaml");
		Map<String, String> data = Map.of("val", "{flow}");
		String result = (String) toYaml.invoke(new Object[] { data });
		assertTrue(result.contains("'{flow}'"), "flow-indicator value must stay single-quoted: " + result);
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
	void testToYamlDoesNotSplitLongLines() {
		Function toYaml = functions().get("toYaml");
		// String longer than 80 chars containing Go template expressions
		String longValue = "{{ if .Values.webserver.defaultUser }}{{ .Values.webserver.defaultUser.role }}"
				+ "{{ else }}{{ .Values.createUserJob.defaultUser.role }}{{ end }}";
		Map<String, Object> data = Map.of("arg", longValue);
		String yaml = (String) toYaml.invoke(new Object[] { data });
		// Must NOT contain backslash line continuation that would break tpl() parsing
		assertFalse(yaml.contains("\\\n"), "toYaml should not split lines with \\ continuation");
		assertTrue(yaml.contains(longValue) || yaml.contains("\"" + longValue + "\""));
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

	@Test
	void testToTomlNestedTablesUseHeaders() {
		// Helm's toToml (Go BurntSushi) emits [table] headers with 2-space-per-depth
		// indentation, not Jackson's dotted keys (mast.sail = ...). Verified against the
		// helm binary.
		Function fn = functions().get("toToml");
		Map<String, Object> inner = new LinkedHashMap<>();
		inner.put("cc", "deepval");
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("xx", "v");
		a.put("bb", inner);
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("a", a);
		assertEquals("[a]\n  xx = \"v\"\n  [a.bb]\n    cc = \"deepval\"\n", fn.invoke(new Object[] { root }));
	}

	@Test
	void testToTomlArrayOfTables() {
		// An array of maps renders as repeated [[key]] sections, each preceded by a blank
		// line (except when it is the first line of output). Verified against the helm
		// binary.
		Function fn = functions().get("toToml");
		Map<String, Object> first = new LinkedHashMap<>();
		first.put("srv", "alpha");
		Map<String, Object> second = new LinkedHashMap<>();
		second.put("srv", "beta");
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("list", List.of(first, second));
		assertEquals("[[list]]\n  srv = \"alpha\"\n\n[[list]]\n  srv = \"beta\"\n", fn.invoke(new Object[] { root }));
	}

}
