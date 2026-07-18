package org.alexmond.jhelm.pluginapi;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JhelmPluginsTest {

	@Test
	void fromServiceLoaderFindsRegisteredService() {
		List<JhelmPostRenderer> found = JhelmPlugins.fromServiceLoader(JhelmPostRenderer.class);
		assertTrue(found.stream().anyMatch((p) -> p instanceof ExampleServicePostRenderer),
				"ServiceLoader should find the registered example");
	}

	@Test
	void mergeUnionsBeansAndServices() {
		List<JhelmPostRenderer> merged = JhelmPlugins.merge(JhelmPostRenderer.class, List.of(new BeanPostRenderer()));
		assertTrue(merged.stream().anyMatch((p) -> p instanceof BeanPostRenderer), "bean present");
		assertTrue(merged.stream().anyMatch((p) -> p instanceof ExampleServicePostRenderer), "service present");
	}

	@Test
	void mergeDeduplicatesByClassPreferringTheSuppliedInstance() {
		ExampleServicePostRenderer bean = new ExampleServicePostRenderer();
		List<JhelmPostRenderer> merged = JhelmPlugins.merge(JhelmPostRenderer.class, List.of(bean));
		long count = merged.stream().filter((p) -> p instanceof ExampleServicePostRenderer).count();
		assertEquals(1, count, "the class that is both a bean and a service appears once");
		assertTrue(merged.contains(bean), "the supplied bean instance is kept");
	}

	@Test
	void defaultNameIsSimpleClassName() {
		assertEquals("BeanPostRenderer", new BeanPostRenderer().name());
	}

	/** A second post-renderer supplied as a "bean" (not a registered service). */
	static class BeanPostRenderer implements JhelmPostRenderer {

		@Override
		public String postRender(String manifest) {
			return manifest.toLowerCase(Locale.ROOT);
		}

	}

}
