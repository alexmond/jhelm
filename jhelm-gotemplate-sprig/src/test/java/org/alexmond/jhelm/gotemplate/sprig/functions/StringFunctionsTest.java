package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StringFunctionsTest {

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

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ upper \"hello\" }}                          | HELLO",
					"{{ lower \"HELLO\" }}                          | hello",
					"{{ title \"hello world\" }}                    | Hello World",
					"{{ repeat 3 \"ab\" }}                          | ababab",
					"{{ substr 0 5 \"Hello World\" }}               | Hello",
					"{{ trim \"  hello  \" }}                       | hello",
					"{{ trimAll \"$\" \"$5.00\" }}                  | 5.00",
					"{{ trimPrefix \"hello \" \"hello world\" }}    | world",
					"{{ trimSuffix \" world\" \"hello world\" }}    | hello",
					"{{ contains \"ell\" \"hello\" }}               | true",
					"{{ hasPrefix \"hel\" \"hello\" }}              | true",
					"{{ hasSuffix \"lo\" \"hello\" }}               | true",
					"{{ cat \"hello\" \"world\" }}                  | hello world",
					"{{ replace \" \" \"_\" \"hello world\" }}      | hello_world",
					"{{ snakecase \"HelloWorld\" }}                  | hello_world",
					"{{ kebabcase \"HelloWorld\" }}                  | hello-world",
					"{{ initials \"John Doe\" }}                    | JD",
					"{{ plural \"item\" \"items\" 1 }}              | item",
					"{{ plural \"item\" \"items\" 2 }}              | items" })
	void testStringFunction(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testIndent() throws IOException, TemplateException {
		assertEquals("  hello", exec("{{ indent 2 \"hello\" }}"));
	}

	@Test
	void testUntitle() throws IOException, TemplateException {
		assertTrue(exec("{{ untitle \"Hello World\" }}").startsWith("hello"));
	}

	@Test
	void testNindent() throws IOException, TemplateException {
		assertEquals("\n  hello", exec("{{ nindent 2 \"hello\" }}"));
	}

	@Test
	void testQuote() throws IOException, TemplateException {
		assertEquals("\"hello\"", exec("{{ quote \"hello\" }}"));
	}

	@Test
	void testQuoteEscapesInnerDoubleQuotes() throws IOException, TemplateException {
		// Longhorn pattern: quote on a JSON string containing double quotes
		HashMap<String, Object> data = new HashMap<>();
		data.put("val", "{\"v1\":\"true\"}");
		StringWriter writer = new StringWriter();
		execute("test", "{{ quote .val }}", data, writer);
		assertEquals("\"{\\\"v1\\\":\\\"true\\\"}\"", writer.toString());
	}

	@Test
	void testQuoteEscapesNewlines() throws IOException, TemplateException {
		// oauth2-proxy pattern: multi-line config in quote
		HashMap<String, Object> data = new HashMap<>();
		data.put("val", "line1\nline2");
		StringWriter writer = new StringWriter();
		execute("test", "{{ quote .val }}", data, writer);
		assertEquals("\"line1\\nline2\"", writer.toString());
	}

	@Test
	void testSquote() throws IOException, TemplateException {
		assertEquals("'hello'", exec("{{ squote \"hello\" }}"));
	}

	@Test
	void testCamelcase() throws IOException, TemplateException {
		String result = exec("{{ camelcase \"hello_world\" }}");
		assertTrue(result.contains("Hello") || result.contains("hello"));
	}

	@Test
	void testShuffle() throws IOException, TemplateException {
		assertEquals(3, exec("{{ shuffle \"abc\" }}").length());
	}

	@Test
	void testWrap() throws IOException, TemplateException {
		assertFalse(exec("{{ wrap 5 \"hello world\" }}").isEmpty());
	}

	@Test
	void testWrapWith() throws IOException, TemplateException {
		assertFalse(exec("{{ wrapWith 5 \"\\n\" \"hello world\" }}").isEmpty());
	}

	@Test
	void testAbbrev() throws IOException, TemplateException {
		assertTrue(exec("{{ abbrev 5 \"hello world\" }}").length() <= 8);
	}

	@Test
	void testAbbrevboth() throws IOException, TemplateException {
		assertTrue(exec("{{ abbrevboth 5 8 \"hello world\" }}").length() <= 11);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ regexMatch \"^[a-z]+$\" \"hello\" }}       | true",
					"{{ mustRegexMatch \"^[a-z]+$\" \"hello\" }}   | true",
					"{{ regexMatch \"[0-9]+\" \"abc123def\" }}      | true",
					"{{ mustRegexMatch \"[0-9]+\" \"abc123def\" }}  | true",
					"{{ regexMatch \"xyz\" \"abc123def\" }}          | false",
					"{{ regexFind \"[0-9]+\" \"abc123def\" }}       | 123",
					"{{ mustRegexFind \"[0-9]+\" \"abc123def\" }}   | 123",
					"{{ regexReplaceAll \"[0-9]+\" \"abc123def456\" \"X\" }} | abcXdefX",
					"{{ mustRegexReplaceAll \"[0-9]+\" \"abc123def\" \"X\" }} | abcXdef" })
	void testRegexFunction(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testRegexMatchSubstring() throws IOException, TemplateException {
		// Go's regexMatch finds pattern anywhere in string, not full-string match
		HashMap<String, Object> data = new HashMap<>();
		data.put("config", "  [runners.kubernetes]\n    namespace = \"default\"\n    image = \"alpine\"");
		StringWriter writer = new StringWriter();
		execute("test", "{{ regexMatch \"\\\\s*namespace\\\\s*=\" .config }}", data, writer);
		assertEquals("true", writer.toString());
	}

	@Test
	void testMustRegexMatchSubstring() throws IOException, TemplateException {
		HashMap<String, Object> data = new HashMap<>();
		data.put("config", "  [runners.kubernetes]\n    namespace = \"default\"");
		StringWriter writer = new StringWriter();
		execute("test", "{{ mustRegexMatch \"\\\\s*namespace\\\\s*=\" .config }}", data, writer);
		assertEquals("true", writer.toString());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ $matches := regexFindAll \"[0-9]+\" \"abc123def456\" -1 }}{{ len $matches }} | 2",
					"{{ $parts := regexSplit \"[,;]\" \"a,b;c\" -1 }}{{ len $parts }}                | 3",
					"{{ $parts := mustRegexSplit \"[,;]\" \"a,b;c\" -1 }}{{ len $parts }}            | 3" })
	void testRegexCollectionFunction(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testRegexReplaceAllLiteral() throws IOException, TemplateException {
		assertFalse(exec("{{ regexReplaceAllLiteral \"[0-9]+\" \"abc123def456\" \"X\" }}").isEmpty());
	}

	// --- quote/squote null handling ---

	@Test
	void testQuoteNull() throws IOException, TemplateException {
		// Go Sprig quote skips nil values entirely, returning empty string
		HashMap<String, Object> data = new HashMap<>();
		data.put("val", null);
		StringWriter writer = new StringWriter();
		execute("test", "{{ quote .val }}", data, writer);
		assertEquals("", writer.toString());
	}

	@Test
	void testSquoteNull() throws IOException, TemplateException {
		// Go Sprig squote skips nil values entirely, returning empty string
		HashMap<String, Object> data = new HashMap<>();
		data.put("val", null);
		StringWriter writer = new StringWriter();
		execute("test", "{{ squote .val }}", data, writer);
		assertEquals("", writer.toString());
	}

	@Test
	void testQuoteMultipleArgs() throws IOException, TemplateException {
		// Go Sprig quote joins multiple non-nil args with space
		assertEquals("\"a\" \"b\" \"c\"", exec("{{ quote \"a\" \"b\" \"c\" }}"));
	}

	@Test
	void testQuoteMultipleArgsWithNull() throws IOException, TemplateException {
		// Go Sprig quote skips nil values in multi-arg call
		HashMap<String, Object> data = new HashMap<>();
		data.put("a", "hello");
		data.put("b", null);
		data.put("c", "world");
		StringWriter writer = new StringWriter();
		execute("test", "{{ quote .a .b .c }}", data, writer);
		assertEquals("\"hello\" \"world\"", writer.toString());
	}

	@Test
	void testQuoteEmptyString() throws IOException, TemplateException {
		// Empty string (not null) should produce quoted empty string
		assertEquals("\"\"", exec("{{ quote \"\" }}"));
	}

	@Test
	void testNullGuardPatternWithQuote() throws IOException, TemplateException {
		// Cert-manager null-guard pattern: quote(nil) produces "", which is in the
		// list, so the block should be skipped
		HashMap<String, Object> data = new HashMap<>();
		data.put("val", null);
		StringWriter writer = new StringWriter();
		String template = """
				{{- if not (has (quote .val) (list "" (quote ""))) -}}visible{{- end -}}""";
		execute("test", template, data, writer);
		assertEquals("", writer.toString());
	}

	@Test
	void testNullGuardPatternWithNonNullValue() throws IOException, TemplateException {
		// When value is non-null, the guard should allow rendering
		HashMap<String, Object> data = new HashMap<>();
		data.put("val", 10);
		StringWriter writer = new StringWriter();
		String template = """
				{{- if not (has (quote .val) (list "" (quote ""))) -}}visible{{- end -}}""";
		execute("test", template, data, writer);
		assertEquals("visible", writer.toString());
	}

	@Test
	void testIfBlockSkippedForNullValue() throws IOException, TemplateException {
		// {{if .nullVal}} should evaluate to false and skip the block
		HashMap<String, Object> data = new HashMap<>();
		data.put("nullVal", null);
		StringWriter writer = new StringWriter();
		execute("test", "{{- if .nullVal }}visible{{- end -}}", data, writer);
		assertEquals("", writer.toString());
	}

	// --- New functions: nospace, swapcase, regexQuoteMeta, mustRegexFindAll, trimall ---

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ nospace \"hello world\" }}                | helloworld",
					"{{ nospace \"  a b  c  \" }}                  | abc",
					"{{ nospace \"nospaces\" }}                    | nospaces" })
	void testNospace(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ swapcase \"Hello World\" }}               | hELLO wORLD",
					"{{ swapcase \"ABC\" }}                        | abc",
					"{{ swapcase \"abc\" }}                        | ABC",
					"{{ swapcase \"123\" }}                        | 123" })
	void testSwapcase(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testRegexQuoteMeta() throws IOException, TemplateException {
		String result = exec("{{ regexQuoteMeta \"1.2.3\" }}");
		assertTrue(result.contains("1"));
		assertTrue(result.contains("2"));
		assertTrue(result.contains("3"));
	}

	@Test
	void testMustRegexFindAll() throws IOException, TemplateException {
		assertEquals("2", exec("{{ $m := mustRegexFindAll \"[0-9]+\" \"abc123def456\" -1 }}{{ len $m }}"));
	}

	@Test
	void testTrimallAlias() throws IOException, TemplateException {
		assertEquals("5.00", exec("{{ trimall \"$\" \"$5.00\" }}"));
	}

}
