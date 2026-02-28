package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

class NetworkFunctionsTest {

	private String exec(String template) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		GoTemplate t = new GoTemplate();
		t.parse("test", template);
		t.execute("test", new HashMap<>(), writer);
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

}
