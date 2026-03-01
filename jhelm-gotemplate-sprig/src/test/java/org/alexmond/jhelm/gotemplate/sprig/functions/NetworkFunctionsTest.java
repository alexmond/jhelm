package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

class NetworkFunctionsTest {

	private String exec(String template) throws IOException, TemplateException {
		return exec(template, new HashMap<>());
	}

	private String exec(String template, Map<String, Object> data) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		GoTemplate t = new GoTemplate();
		t.parse("test", template);
		t.execute("test", data, writer);
		return writer.toString();
	}

	@Test
	void testGetHostByNameLocalhost() throws IOException, TemplateException {
		String result = exec("{{ getHostByName \"localhost\" }}");
		assertNotNull(result);
		assertFalse(result.isEmpty());
	}

	@Test
	void testGetHostByNameInvalid() throws IOException, TemplateException {
		String result = exec("{{ getHostByName \"this.host.does.not.exist.invalid\" }}");
		assertNotNull(result);
	}

	@Test
	void testUrlJoin() throws IOException, TemplateException {
		String template = "{{ urlJoin (dict \"scheme\" \"https\" \"host\" \"example.com\" \"port\" \"8443\" \"path\" \"/api/v1\") }}";
		assertEquals("https://example.com:8443/api/v1", exec(template));
	}

	@Test
	void testUrlJoinSimple() throws IOException, TemplateException {
		String template = "{{ urlJoin (dict \"scheme\" \"http\" \"host\" \"localhost\") }}";
		assertEquals("http://localhost", exec(template));
	}

	@Test
	void testUrlJoinWithQuery() throws IOException, TemplateException {
		String template = "{{ urlJoin (dict \"scheme\" \"https\" \"host\" \"example.com\" \"query\" \"foo=bar\") }}";
		assertEquals("https://example.com?foo=bar", exec(template));
	}

}
