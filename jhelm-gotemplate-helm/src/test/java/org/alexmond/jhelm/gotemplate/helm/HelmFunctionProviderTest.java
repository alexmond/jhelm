package org.alexmond.jhelm.gotemplate.helm;

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

class HelmFunctionProviderTest {

	@Test
	void testPriority() {
		HelmFunctionProvider provider = new HelmFunctionProvider();
		assertEquals(200, provider.priority());
	}

	@Test
	void testName() {
		HelmFunctionProvider provider = new HelmFunctionProvider();
		assertEquals("Helm", provider.name());
	}

	@Test
	void testGetFunctionsReturnsHelmFunctions() {
		HelmFunctionProvider provider = new HelmFunctionProvider();
		GoTemplate template = GoTemplate.builder().noAutoDiscovery().build();
		Map<String, Function> functions = provider.getFunctions(template);

		assertFalse(functions.isEmpty());
		assertNotNull(functions.get("toYaml"));
		assertNotNull(functions.get("toJson"));
		assertNotNull(functions.get("fromYaml"));
		assertNotNull(functions.get("fromJson"));
		assertNotNull(functions.get("include"));
		assertNotNull(functions.get("tpl"));
		assertNotNull(functions.get("required"));
		assertNotNull(functions.get("lookup"));
	}

	@Test
	void testNoArgConstructorProvideStubKubernetes() {
		HelmFunctionProvider provider = new HelmFunctionProvider();
		GoTemplate template = GoTemplate.builder().noAutoDiscovery().build();
		Map<String, Function> functions = provider.getFunctions(template);

		// lookup should return empty map (stub)
		Function lookup = functions.get("lookup");
		assertNotNull(lookup);
	}

	@Test
	void testServiceLoaderDiscovery() {
		ServiceLoader<FunctionProvider> loader = ServiceLoader.load(FunctionProvider.class);
		boolean found = false;
		for (FunctionProvider provider : loader) {
			if (provider instanceof HelmFunctionProvider) {
				found = true;
				break;
			}
		}
		assertTrue(found, "HelmFunctionProvider should be discoverable via ServiceLoader");
	}

	@Test
	void testBuilderWithHelmProvider() throws Exception {
		GoTemplate template = GoTemplate.builder().noAutoDiscovery().withProvider(new HelmFunctionProvider()).build();

		// Should have Go builtins and Helm functions
		assertTrue(template.getFunctions().containsKey("len"));
		assertTrue(template.getFunctions().containsKey("toYaml"));
		assertTrue(template.getFunctions().containsKey("include"));

		template.parse("test", "{{ .data | toJson }}");
		StringWriter writer = new StringWriter();
		template.execute("test", Map.of("data", Map.of("name", "nginx")), writer);
		assertTrue(writer.toString().contains("nginx"));
	}

}
