package org.alexmond.jhelm.core.service;

import java.io.IOException;
import java.util.List;

import org.alexmond.jhelm.pluginapi.JhelmPluginException;
import org.alexmond.jhelm.pluginapi.JhelmPostRenderer;
import org.alexmond.jhelm.pluginapi.JhelmPlugins;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JhelmPostRendererAdapterTest {

	@Test
	void adapterDelegatesToThePlugin() throws Exception {
		PostRenderProcessor processor = new JhelmPostRendererAdapter((manifest) -> manifest + "\n# rendered");
		assertEquals("kind: X\n# rendered", processor.process("kind: X"));
	}

	@Test
	void adapterTranslatesPluginExceptionToIoException() {
		JhelmPostRenderer failing = (manifest) -> {
			throw new JhelmPluginException("boom");
		};
		IOException ex = assertThrows(IOException.class, () -> new JhelmPostRendererAdapter(failing).process("x"));
		assertTrue(ex.getMessage().contains("boom"));
	}

	@Test
	void serviceLoaderPluginIsDiscoveredAdaptedAndApplied() throws Exception {
		List<PostRenderProcessor> processors = JhelmPlugins.merge(JhelmPostRenderer.class, List.of())
			.stream()
			.<PostRenderProcessor>map(JhelmPostRendererAdapter::new)
			.toList();
		String result = processors.stream()
			.filter((p) -> p instanceof JhelmPostRendererAdapter)
			.findFirst()
			.orElseThrow()
			.process("hello");
		// The ServiceLoader-registered UppercaseTestPostRenderer uppercases the manifest.
		assertEquals("HELLO", result);
	}

}
