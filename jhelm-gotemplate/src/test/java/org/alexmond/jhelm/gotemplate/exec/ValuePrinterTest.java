package org.alexmond.jhelm.gotemplate.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValuePrinterTest {

	@ParameterizedTest
	@CsvSource({ "1.0, 1", "0.0, 0", "-5.0, -5", "100.0, 100" })
	void testWholeNumberDoubleRenderedWithoutDecimal(double input, String expected) throws Exception {
		Map<String, Object> data = Map.of("val", input);
		assertEquals(expected, render("{{ .val }}", data));
	}

	@ParameterizedTest
	@CsvSource({ "1.5, 1.5", "3.14, 3.14", "-0.5, -0.5" })
	void testFractionalDoublePreservesDecimal(double input, String expected) throws Exception {
		Map<String, Object> data = Map.of("val", input);
		assertEquals(expected, render("{{ .val }}", data));
	}

	@Test
	void testIntegerUnchanged() throws Exception {
		Map<String, Object> data = Map.of("val", 42);
		assertEquals("42", render("{{ .val }}", data));
	}

	@Test
	void testBooleanUnchanged() throws Exception {
		Map<String, Object> data = Map.of("val", true);
		assertEquals("true", render("{{ .val }}", data));
	}

	@Test
	void testCollectionWithDoubles() throws Exception {
		Map<String, Object> data = Map.of("val", List.of(1.0, 2.5, 3.0));
		assertEquals("[1 2.5 3]", render("{{ .val }}", data));
	}

	@Test
	void testMapWithDoubleValues() throws Exception {
		// Use a single-entry map to avoid ordering issues
		Map<String, Object> data = Map.of("val", Map.of("score", 1.0));
		assertEquals("map[score:1]", render("{{ .val }}", data));
	}

	@Test
	void testStringConcatenation() throws Exception {
		Map<String, Object> data = Map.of("val", 1.0);
		assertEquals("value=1", render("value={{ .val }}", data));
	}

	@ParameterizedTest // expected values captured from `helm template` (Go fmt float64
						// %v)
	@CsvSource({ "0.0, 0", "3.0, 3", "1.5, 1.5", "100000.0, 100000", "999999.0, 999999", "1000000.0, 1e+06",
			"5000000.0, 5e+06", "1234567.0, 1.234567e+06", "12345678.0, 1.2345678e+07", "104857600.0, 1.048576e+08",
			"268435456.0, 2.68435456e+08", "1073741824.0, 1.073741824e+09", "200000000.0, 2e+08", "-5000000.0, -5e+06",
			"12345678.9, 1.23456789e+07", "0.0001, 0.0001", "0.00001, 1e-05", "1.0E15, 1e+15", "1.0E20, 1e+20",
			"1.0E21, 1e+21" })
	void testGoFloatStringMatchesHelm(double input, String expected) {
		assertEquals(expected, org.alexmond.jhelm.gotemplate.GoFmt.floatString(input));
	}

	private String render(String template, Map<String, Object> data) throws TemplateException, IOException {
		GoTemplate tmpl = new GoTemplate();
		tmpl.parse("test", template);
		StringWriter writer = new StringWriter();
		tmpl.execute(data, writer);
		return writer.toString();
	}

}
