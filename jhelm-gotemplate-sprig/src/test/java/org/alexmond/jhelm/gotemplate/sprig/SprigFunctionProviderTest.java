package org.alexmond.jhelm.gotemplate.sprig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.Map;
import java.util.ServiceLoader;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.FunctionProvider;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.junit.jupiter.api.Test;

class SprigFunctionProviderTest {

	@Test
	void testPriority() {
		SprigFunctionProvider provider = new SprigFunctionProvider();
		assertEquals(100, provider.priority());
	}

	@Test
	void testName() {
		SprigFunctionProvider provider = new SprigFunctionProvider();
		assertEquals("Sprig", provider.name());
	}

	@Test
	void testGetFunctionsReturnsSprigFunctions() {
		SprigFunctionProvider provider = new SprigFunctionProvider();
		GoTemplate template = GoTemplate.builder().noAutoDiscovery().build();
		Map<String, Function> functions = provider.getFunctions(template);

		assertFalse(functions.isEmpty());
		assertNotNull(functions.get("upper"));
		assertNotNull(functions.get("lower"));
		assertNotNull(functions.get("trim"));
		assertNotNull(functions.get("list"));
		assertNotNull(functions.get("dict"));
		assertNotNull(functions.get("default"));
		assertNotNull(functions.get("b64enc"));
	}

	@Test
	void testServiceLoaderDiscovery() {
		ServiceLoader<FunctionProvider> loader = ServiceLoader.load(FunctionProvider.class);
		boolean found = false;
		for (FunctionProvider provider : loader) {
			if (provider instanceof SprigFunctionProvider) {
				found = true;
				break;
			}
		}
		assertTrue(found, "SprigFunctionProvider should be discoverable via ServiceLoader");
	}

	@Test
	void testBuilderWithSprigProvider() throws Exception {
		GoTemplate template = GoTemplate.builder().noAutoDiscovery().withProvider(new SprigFunctionProvider()).build();

		// Should have both Go builtins and Sprig functions
		assertTrue(template.getFunctions().containsKey("len"));
		assertTrue(template.getFunctions().containsKey("upper"));

		template.parse("test", "{{ .name | upper }}");
		StringWriter writer = new StringWriter();
		template.execute("test", Map.of("name", "world"), writer);
		assertEquals("WORLD", writer.toString());
	}

}
