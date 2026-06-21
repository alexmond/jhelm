package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictFunctionsTest {

	@SuppressWarnings("unchecked")
	private static Map<String, Object> invoke(String name, Object dst, Map<String, Object> src) {
		Function fn = DictFunctions.getFunctions().get(name);
		return (Map<String, Object>) fn.invoke(new Object[] { dst, src });
	}

	@Test
	void testMergeSkipsNilSourceForAbsentKey() {
		// mergo (Helm's `merge`) does not add an absent key whose source value is nil,
		// but
		// still adds other empty values ("", false, 0). kuma relies on this to drop a
		// null-valued env var.
		Map<String, Object> src = new HashMap<>();
		src.put("nilv", null);
		src.put("emptystr", "");
		src.put("zero", 0);
		src.put("real", "x");
		Map<String, Object> result = invoke("merge", new HashMap<>(), src);
		assertFalse(result.containsKey("nilv"), "merge must drop an absent key with a nil source value");
		assertTrue(result.containsKey("emptystr"), "merge keeps an empty-string value");
		assertTrue(result.containsKey("zero"), "merge keeps a zero value");
		assertEquals("x", result.get("real"));
	}

	@Test
	void testMergeOverwriteKeepsNilSourceForAbsentKey() {
		// mergeOverwrite/mustMergeOverwrite DO add a nil-valued absent key — gotohelm
		// (e.g. redpanda) builds structs with nil fields this way.
		Map<String, Object> src = new HashMap<>();
		src.put("nilv", null);
		Map<String, Object> result = invoke("mergeOverwrite", new HashMap<>(), src);
		assertTrue(result.containsKey("nilv"), "mergeOverwrite keeps a nil-valued absent key");
	}

}
