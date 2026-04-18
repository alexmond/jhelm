package org.alexmond.jhelm.gotemplate.helm.functions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateFunctionsTest {

	private GoTemplate template;

	private Map<String, Function> functions;

	@BeforeEach
	void setUp() {
		template = new GoTemplate();
		functions = TemplateFunctions.getFunctions(template);
	}

	@Test
	void testGetFunctionsReturnsAllFunctions() {
		assertEquals(5, functions.size());
		assertTrue(functions.containsKey("include"));
		assertTrue(functions.containsKey("mustInclude"));
		assertTrue(functions.containsKey("tpl"));
		assertTrue(functions.containsKey("mustTpl"));
		assertTrue(functions.containsKey("required"));
	}

	// --- include tests ---

	@Test
	void testIncludeReturnsEmptyOnInsufficientArgs() throws Exception {
		Function include = functions.get("include");
		assertEquals("", include.invoke(new Object[] {}));
		assertEquals("", include.invoke(new Object[] { "name" }));
	}

	@Test
	void testIncludeExecutesNamedTemplate() throws Exception {
		template.parse("greeting", "Hello {{ . }}!");
		Function include = functions.get("include");
		String result = (String) include.invoke(new Object[] { "greeting", "World" });
		assertEquals("Hello World!", result);
	}

	@Test
	void testIncludeThrowsOnMissingTemplate() {
		Function include = functions.get("include");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> include.invoke(new Object[] { "nonexistent", "data" }));
		assertTrue(ex.getMessage().contains("nonexistent"));
	}

	@Test
	void testIncludeOfEmptyTemplateReturnsEmptyString() throws Exception {
		// Mirrors rabbitmq validation pattern: include a template that produces nothing
		template.parse("check", "{{- define \"check\" -}}{{- if false -}}error{{- end -}}{{- end -}}");
		Function include = functions.get("include");
		String result = (String) include.invoke(new Object[] { "check", new HashMap<>() });
		assertEquals("", result, "include of empty-producing template should return \"\"");
	}

	@Test
	void testIncludeOfConditionallyEmptyTemplate() throws Exception {
		// Include a template with a false condition and trim markers — should return ""
		template.parse("helpers", "{{- define \"check1\" -}}{{- if false -}}error{{- end -}}{{- end -}}");
		Function include = functions.get("include");
		String result = (String) include.invoke(new Object[] { "check1", new HashMap<>() });
		assertEquals("", result, "include of conditionally empty template should return empty string");
	}

	// --- mustInclude tests ---

	@Test
	void testMustIncludeThrowsOnInsufficientArgs() {
		Function mustInclude = functions.get("mustInclude");
		RuntimeException ex = assertThrows(RuntimeException.class, () -> mustInclude.invoke(new Object[] {}));
		assertTrue(ex.getMessage().contains("insufficient arguments"));
	}

	@Test
	void testMustIncludeExecutesNamedTemplate() throws Exception {
		template.parse("greeting", "Hi {{ . }}!");
		Function mustInclude = functions.get("mustInclude");
		String result = (String) mustInclude.invoke(new Object[] { "greeting", "There" });
		assertEquals("Hi There!", result);
	}

	@Test
	void testMustIncludeThrowsOnMissingTemplate() {
		Function mustInclude = functions.get("mustInclude");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> mustInclude.invoke(new Object[] { "nonexistent", "data" }));
		assertTrue(ex.getMessage().contains("nonexistent"));
	}

	// --- tpl tests ---

	@Test
	void testTplReturnsEmptyOnInsufficientArgs() throws Exception {
		Function tpl = functions.get("tpl");
		assertEquals("", tpl.invoke(new Object[] {}));
		assertEquals("", tpl.invoke(new Object[] { "text" }));
	}

	@Test
	void testTplEvaluatesInlineTemplate() throws Exception {
		Function tpl = functions.get("tpl");
		Map<String, Object> data = Map.of("name", "World");
		String result = (String) tpl.invoke(new Object[] { "Hello {{ .name }}!", data });
		assertEquals("Hello World!", result);
	}

	@Test
	void testTplWithPlainText() throws Exception {
		Function tpl = functions.get("tpl");
		String result = (String) tpl.invoke(new Object[] { "plain text", Map.of() });
		assertEquals("plain text", result);
	}

	@Test
	void testTplThrowsOnSyntaxError() {
		Function tpl = functions.get("tpl");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> tpl.invoke(new Object[] { "{{ .invalid }", Map.of() }));
		assertTrue(ex.getMessage().contains("tpl"));
	}

	// --- mustTpl tests ---

	@Test
	void testMustTplThrowsOnInsufficientArgs() {
		Function mustTpl = functions.get("mustTpl");
		RuntimeException ex = assertThrows(RuntimeException.class, () -> mustTpl.invoke(new Object[] {}));
		assertTrue(ex.getMessage().contains("insufficient arguments"));
	}

	@Test
	void testMustTplEvaluatesInlineTemplate() throws Exception {
		Function mustTpl = functions.get("mustTpl");
		Map<String, Object> data = Map.of("val", "42");
		String result = (String) mustTpl.invoke(new Object[] { "result={{ .val }}", data });
		assertEquals("result=42", result);
	}

	@Test
	void testMustTplThrowsOnSyntaxError() {
		Function mustTpl = functions.get("mustTpl");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> mustTpl.invoke(new Object[] { "{{ .bad }", Map.of() }));
		assertTrue(ex.getMessage().contains("mustTpl"));
	}

	// --- required tests ---

	@Test
	void testRequiredThrowsOnInsufficientArgs() {
		Function required = functions.get("required");
		RuntimeException ex = assertThrows(RuntimeException.class, () -> required.invoke(new Object[] {}));
		assertTrue(ex.getMessage().contains("insufficient arguments"));
	}

	@Test
	void testRequiredThrowsOnNullValue() {
		Function required = functions.get("required");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> required.invoke(new Object[] { "value is required", null }));
		assertEquals("value is required", ex.getMessage());
	}

	@Test
	void testRequiredThrowsOnEmptyString() {
		Function required = functions.get("required");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> required.invoke(new Object[] { "must not be empty", "" }));
		assertEquals("must not be empty", ex.getMessage());
	}

	@Test
	void testRequiredThrowsOnFalse() {
		Function required = functions.get("required");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> required.invoke(new Object[] { "must be truthy", false }));
		assertEquals("must be truthy", ex.getMessage());
	}

	@Test
	void testRequiredThrowsOnEmptyCollection() {
		Function required = functions.get("required");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> required.invoke(new Object[] { "list required", Collections.emptyList() }));
		assertEquals("list required", ex.getMessage());
	}

	@Test
	void testRequiredThrowsOnEmptyMap() {
		Function required = functions.get("required");
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> required.invoke(new Object[] { "map required", Collections.emptyMap() }));
		assertEquals("map required", ex.getMessage());
	}

	@Test
	void testRequiredPassesNonEmptyString() throws Exception {
		Function required = functions.get("required");
		Object result = required.invoke(new Object[] { "error msg", "hello" });
		assertEquals("hello", result);
	}

	@Test
	void testRequiredPassesNonZeroNumber() throws Exception {
		Function required = functions.get("required");
		Object result = required.invoke(new Object[] { "error msg", 42 });
		assertEquals(42, result);
	}

	@Test
	void testRequiredPassesTrue() throws Exception {
		Function required = functions.get("required");
		Object result = required.invoke(new Object[] { "error msg", true });
		assertEquals(true, result);
	}

	@Test
	void testRequiredPassesNonEmptyList() throws Exception {
		Function required = functions.get("required");
		List<String> list = List.of("a");
		Object result = required.invoke(new Object[] { "error msg", list });
		assertEquals(list, result);
	}

	// --- integration: include with tpl-defined templates ---

	@Test
	void testIncludeWithDefinedTemplate() throws Exception {
		template.parse("helpers", "{{ define \"myHelper\" }}helper-output{{ end }}");
		Function include = functions.get("include");
		String result = (String) include.invoke(new Object[] { "myHelper", Map.of() });
		assertEquals("helper-output", result);
	}

	@Test
	void testTplWithMapContext() throws Exception {
		Function tpl = functions.get("tpl");
		Map<String, Object> data = new HashMap<>();
		data.put("host", "example.com");
		data.put("port", 8080);
		String tmpl = "{{ .host }}:{{ .port }}";
		String result = (String) tpl.invoke(new Object[] { tmpl, data });
		assertEquals("example.com:8080", result);
	}

}
