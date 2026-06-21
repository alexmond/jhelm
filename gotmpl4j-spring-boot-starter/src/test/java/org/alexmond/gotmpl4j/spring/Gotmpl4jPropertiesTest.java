package org.alexmond.gotmpl4j.spring;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gotmpl4jPropertiesTest {

	@Test
	void defaultsMatchTheDocumentedConventions() {
		Gotmpl4jProperties properties = new Gotmpl4jProperties();

		assertTrue(properties.isEnabled());
		assertEquals("classpath:/templates/", properties.getTemplateLocation());
		assertEquals(".gotmpl", properties.getSuffix());
		assertEquals(StandardCharsets.UTF_8, properties.getCharset());
		assertTrue(properties.isCache());
	}

	@Test
	void accessorsRoundTrip() {
		Gotmpl4jProperties properties = new Gotmpl4jProperties();

		properties.setEnabled(false);
		properties.setTemplateLocation("file:/srv/views/");
		properties.setSuffix(".tmpl");
		properties.setCharset(StandardCharsets.ISO_8859_1);
		properties.setCache(false);

		assertFalse(properties.isEnabled());
		assertEquals("file:/srv/views/", properties.getTemplateLocation());
		assertEquals(".tmpl", properties.getSuffix());
		assertEquals(StandardCharsets.ISO_8859_1, properties.getCharset());
		assertFalse(properties.isCache());
	}

}
