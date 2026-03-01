package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class LogicFunctionsTest {

	private void execute(String name, String text, Object data, StringWriter writer)
			throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse(name, text);
		template.execute(name, data, writer);
	}

	private String exec(String template, Map<String, Object> data) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		execute("test", template, data, writer);
		return writer.toString();
	}

	// Test 'empty' function

	@ParameterizedTest
	@MethodSource("emptyTestCases")
	void testEmpty(Object value, String expected) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", value);
		assertEquals(expected, exec("{{ empty .value }}", data));
	}

	static Stream<Arguments> emptyTestCases() {
		Map<String, Object> nonEmptyMap = new HashMap<>();
		nonEmptyMap.put("key", "value");
		return Stream.of(Arguments.of(null, "true"), Arguments.of("", "true"), Arguments.of("text", "false"),
				Arguments.of(0, "true"), Arguments.of(5, "false"), Arguments.of(false, "true"),
				Arguments.of(true, "false"), Arguments.of(Collections.emptyList(), "true"),
				Arguments.of(Arrays.asList("a", "b"), "false"), Arguments.of(Collections.emptyMap(), "true"),
				Arguments.of(nonEmptyMap, "false"));
	}

	// Test 'default' function

	@ParameterizedTest
	@MethodSource("defaultStringTestCases")
	void testDefaultWithStrings(Object value, String expected) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", value);
		assertEquals(expected, exec("{{ default \"fallback\" .value }}", data));
	}

	static Stream<Arguments> defaultStringTestCases() {
		return Stream.of(Arguments.of("actual", "actual"), Arguments.of("", "fallback"),
				Arguments.of(null, "fallback"));
	}

	@ParameterizedTest
	@MethodSource("defaultNumberTestCases")
	void testDefaultWithNumbers(Object value, String expected) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", value);
		assertEquals(expected, exec("{{ default 42 .value }}", data));
	}

	static Stream<Arguments> defaultNumberTestCases() {
		return Stream.of(Arguments.of(0, "42"), Arguments.of(10, "10"));
	}

	@Test
	void testDefaultWithFalse() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", false);
		assertEquals("true", exec("{{ default true .value }}", data));
	}

	@Test
	void testDefaultWithEmptyList() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", Collections.emptyList());
		assertEquals("2", exec(
				"{{ $default := list \"a\" \"b\" }}{{ $result := default $default .value }}{{ len $result }}", data));
	}

	@Test
	void testDefaultWithNonEmptyList() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", Arrays.asList("x", "y"));
		assertEquals("2", exec(
				"{{ $default := list \"a\" \"b\" }}{{ $result := default $default .value }}{{ len $result }}", data));
	}

	@Test
	void testDefaultWithEmptyMap() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", Collections.emptyMap());
		assertEquals("1",
				exec("{{ $default := dict \"key\" \"val\" }}{{ $result := default $default .value }}{{ len $result }}",
						data));
	}

	// Test 'coalesce' function

	@Test
	void testCoalesceReturnsFirstTruthyValue() throws IOException, TemplateException {
		assertEquals("first", exec("{{ coalesce \"\" 0 \"first\" \"second\" }}", new HashMap<>()));
	}

	@Test
	void testCoalesceWithAllFalsy() throws IOException, TemplateException {
		assertEquals("", exec("{{ coalesce \"\" 0 false }}", new HashMap<>()));
	}

	@ParameterizedTest
	@MethodSource("coalesceTestCases")
	void testCoalesceWithData(Object a, Object b, String template, String expected)
			throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("a", a);
		data.put("b", b);
		assertEquals(expected, exec(template, data));
	}

	static Stream<Arguments> coalesceTestCases() {
		return Stream.of(Arguments.of(null, "value", "{{ coalesce .a .b }}", "value"),
				Arguments.of(0, 42, "{{ coalesce .a .b }}", "42"));
	}

	@Test
	void testCoalesceWithCollections() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("empty", Collections.emptyList());
		data.put("full", Arrays.asList("a", "b"));
		assertEquals("2", exec("{{ $result := coalesce .empty .full }}{{ len $result }}", data));
	}

	// Test 'ternary' function

	@ParameterizedTest
	@MethodSource("ternaryTestCases")
	void testTernary(Object condition, String expected) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("condition", condition);
		assertEquals(expected, exec("{{ ternary \"yes\" \"no\" .condition }}", data));
	}

	static Stream<Arguments> ternaryTestCases() {
		return Stream.of(Arguments.of(true, "yes"), Arguments.of(false, "no"), Arguments.of("text", "yes"),
				Arguments.of("", "no"), Arguments.of(1, "yes"), Arguments.of(0, "no"), Arguments.of(null, "no"),
				Arguments.of(Arrays.asList("a", "b"), "yes"), Arguments.of(Collections.emptyList(), "no"));
	}

	// Test 'fail' function

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ fail .error }}                    | Custom error message",
					"{{ fail }}                            | ",
					"{{ fail \"Something went wrong\" }}   | Something went wrong" })
	void testFailThrowsException(String template, String errorValue) {
		Map<String, Object> data = new HashMap<>();
		data.put("error", "Custom error message");
		assertThrows(TemplateException.class, () -> exec(template, data));
	}

	// Test with non-standard types

	@Test
	void testEmptyWithCustomObject() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", new Object());
		assertEquals("false", exec("{{ empty .value }}", data));
	}

	@Test
	void testDefaultWithCustomObject() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", new Object());
		String result = exec("{{ default \"fallback\" .value }}", data);
		assertFalse(result.isEmpty());
		assertNotEquals("fallback", result);
	}

	// Issue #137: nested parenthesized function calls as arguments

	@Test
	void testTernaryWithParenthesizedFunctionArg() throws IOException, TemplateException {
		// kindIs "string" "hello" → true, so ternary returns first arg "a"
		assertEquals("a", exec("{{ ternary \"a\" \"b\" (kindIs \"string\" \"hello\") }}", new HashMap<>()));
	}

	@Test
	void testTernaryWithParenthesizedPipeline() throws IOException, TemplateException {
		// (.value | upper) → "HELLO", so ternary returns first arg "yes"
		Map<String, Object> data = new HashMap<>();
		data.put("value", "hello");
		assertEquals("yes", exec("{{ ternary \"yes\" \"no\" (.value | upper) }}", data));
	}

	@Test
	void testTernaryWithMultipleParenthesizedArgs() throws IOException, TemplateException {
		// Full pgadmin4-style: tpl (ternary arg1 arg2 (kindIs ...))
		Map<String, Object> data = new HashMap<>();
		data.put("toMap", "hello");
		assertEquals("hello", exec("{{ ternary .toMap (.toMap | upper) (kindIs \"string\" .toMap) }}", data));
	}

	@Test
	void testNestedParensInsideParens() throws IOException, TemplateException {
		// Issue #137: parens inside parens — (ternary X (Y | Z) (kindIs ...))
		Map<String, Object> data = new HashMap<>();
		data.put("toMap", "hello");
		assertEquals("hello", exec("{{ (ternary .toMap (.toMap | upper) (kindIs \"string\" .toMap)) }}", data));
	}

	// Test 'all' function

	@Test
	void testAllTruthyValues() throws IOException, TemplateException {
		assertEquals("true", exec("{{ all \"a\" 1 true }}", new HashMap<>()));
	}

	@Test
	void testAllWithFalsyValue() throws IOException, TemplateException {
		assertEquals("false", exec("{{ all \"a\" 0 true }}", new HashMap<>()));
	}

	@Test
	void testAllWithEmptyString() throws IOException, TemplateException {
		assertEquals("false", exec("{{ all \"a\" \"\" \"b\" }}", new HashMap<>()));
	}

	@Test
	void testAllNoArgs() throws IOException, TemplateException {
		assertEquals("true", exec("{{ all }}", new HashMap<>()));
	}

	// Test 'any' function

	@Test
	void testAnyTruthyValue() throws IOException, TemplateException {
		assertEquals("true", exec("{{ any \"\" 0 \"hello\" }}", new HashMap<>()));
	}

	@Test
	void testAnyAllFalsy() throws IOException, TemplateException {
		assertEquals("false", exec("{{ any \"\" 0 false }}", new HashMap<>()));
	}

	@Test
	void testAnyNoArgs() throws IOException, TemplateException {
		assertEquals("false", exec("{{ any }}", new HashMap<>()));
	}

}
