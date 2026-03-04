package org.alexmond.jhelm.gotemplate.internal.exec;

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

	private String render(String template, Map<String, Object> data) throws TemplateException, IOException {
		GoTemplate tmpl = new GoTemplate();
		tmpl.parse("test", template);
		StringWriter writer = new StringWriter();
		tmpl.execute(data, writer);
		return writer.toString();
	}

}
