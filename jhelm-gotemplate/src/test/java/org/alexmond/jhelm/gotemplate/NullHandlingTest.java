package org.alexmond.jhelm.gotemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests that null/nil values do not produce the string "null" when rendered in templates.
 * In Go, nil values are omitted from template output; Java's String.valueOf(null) returns
 * "null" which must be avoided.
 */
class NullHandlingTest {

	@Test
	void testNullValueOmittedFromOutput() throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("name", null);
		assertEquals("prefix-suffix", render("prefix-{{ .name }}suffix", data));
	}

	@Test
	void testNullInPrintProducesEmpty() throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("val", null);
		assertEquals("hello", render("{{ print \"hello\" .val }}", data));
	}

	@Test
	void testNullInPrintfProducesEmpty() throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("name", null);
		assertEquals("release-", render("{{ printf \"%s-%s\" \"release\" .name }}", data));
	}

	@Test
	void testNullInPrintlnProducesEmpty() throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("val", null);
		assertEquals("hello \n", render("{{ println \"hello\" .val }}", data));
	}

	@Test
	void testMissingFieldProducesEmpty() throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("config", new HashMap<>());
		// Accessing a missing key in a map returns null
		assertEquals("value=", render("value={{ .config.name }}", data));
	}

	private String render(String template, Map<String, Object> data) throws TemplateException, IOException {
		GoTemplate tmpl = new GoTemplate();
		tmpl.parse("test", template);
		StringWriter writer = new StringWriter();
		tmpl.execute(data, writer);
		return writer.toString();
	}

}
