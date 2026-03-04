package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MathFunctionsTest {

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
			value = { "{{ add 2 3 }}       | 5", "{{ add1 5 }}        | 6", "{{ sub 5 3 }}       | 2",
					"{{ mul 3 4 }}       | 12", "{{ div 10 2 }}      | 5", "{{ div 1 2 }}       | 0",
					"{{ div 7 3 }}       | 2", "{{ mod 10 3 }}      | 1", "{{ max 2 5 3 }}     | 5",
					"{{ min 2 5 3 }}     | 2", "{{ len \"hello\" }} | 5", "{{ int \"42\" }}    | 42",
					"{{ int64 \"123\" }} | 123", "{{ toString 42 }}   | 42", "{{ min 5 }}         | 5",
					"{{ max 5 }}         | 5", "{{ add }}           | 0" })
	void testExactMathFunction(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ ceil 3.2 }}      | 4", "{{ floor 3.8 }}     | 3", "{{ round 3.5 0 }}   | 4",
					"{{ addf 2.5 3.5 }}  | 6", "{{ mulf 2.5 4.0 }}  | 10", "{{ divf 10.0 2.0 }} | 5",
					"{{ float64 \"3.14\" }} | 3.14" })
	void testApproxMathFunction(String template, String expectedContains) throws IOException, TemplateException {
		assertTrue(exec(template).contains(expectedContains));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "{{ $s := seq 1 3 }}{{ len $s }}              | 3",
			"{{ $u := until 3 }}{{ len $u }}              | 3", "{{ $u := untilStep 0 10 2 }}{{ len $u }}     | 5" })
	void testSequenceFunction(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	// --- atoi, toStrings, toDecimal ---

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "{{ atoi \"42\" }}     | 42", "{{ atoi \"0\" }}      | 0",
			"{{ atoi \"-7\" }}     | -7", "{{ atoi \"abc\" }}    | 0" })
	void testAtoi(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testAtoiNullReturnsZero() {
		Function fn = MathFunctions.getFunctions().get("atoi");
		assertEquals(0, fn.invoke(new Object[] {}));
		assertEquals(0, fn.invoke(new Object[] { null }));
	}

	@Test
	void testToStringsReturnsList() {
		Function fn = MathFunctions.getFunctions().get("toStrings");
		Object result = fn.invoke(new Object[] { List.of(1, 2, 3) });
		assertInstanceOf(List.class, result);
		assertEquals(List.of("1", "2", "3"), result);
	}

	@Test
	void testToStringsNullReturnsEmptyList() {
		Function fn = MathFunctions.getFunctions().get("toStrings");
		assertEquals(List.of(), fn.invoke(new Object[] {}));
		assertEquals(List.of(), fn.invoke(new Object[] { null }));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "0777 | 511", "0644 | 420", "0755 | 493" })
	void testToDecimal(String input, long expected) {
		Function fn = MathFunctions.getFunctions().get("toDecimal");
		assertEquals(expected, fn.invoke(new Object[] { input }));
	}

	@Test
	void testToDecimalNullReturnsZero() {
		Function fn = MathFunctions.getFunctions().get("toDecimal");
		assertEquals(0L, fn.invoke(new Object[] {}));
		assertEquals(0L, fn.invoke(new Object[] { null }));
	}

	// --- New functions: randInt, add1f, subf, maxf, minf, biggest ---

	@Test
	void testRandIntRange() throws IOException, TemplateException {
		Function fn = MathFunctions.getFunctions().get("randInt");
		for (int i = 0; i < 50; i++) {
			int result = (int) fn.invoke(new Object[] { 10, 20 });
			assertTrue(result >= 10 && result < 20, "randInt should be in [10,20): " + result);
		}
	}

	@Test
	void testRandIntInTemplate() throws IOException, TemplateException {
		String result = exec("{{ randInt 0 100 }}");
		int value = Integer.parseInt(result);
		assertTrue(value >= 0 && value < 100);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "{{ add1f 2.5 }} | 3.5", "{{ add1f 0.0 }} | 1" })
	void testAdd1f(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testSubf() throws IOException, TemplateException {
		assertEquals("1.5", exec("{{ subf 3.5 2.0 }}"));
	}

	@Test
	void testMaxf() throws IOException, TemplateException {
		assertEquals("5.5", exec("{{ maxf 2.5 5.5 3.0 }}"));
	}

	@Test
	void testMinf() throws IOException, TemplateException {
		assertEquals("2.5", exec("{{ minf 2.5 5.5 3.0 }}"));
	}

	@Test
	void testBiggestAlias() throws IOException, TemplateException {
		assertEquals("5", exec("{{ biggest 2 5 3 }}"));
	}

}
