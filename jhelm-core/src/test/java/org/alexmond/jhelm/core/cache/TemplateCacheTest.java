package org.alexmond.jhelm.core.cache;

import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.internal.parse.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TemplateCacheTest {

	private TemplateCache cache;

	@BeforeEach
	void setUp() {
		cache = new TemplateCache(10);
	}

	@Test
	void putAndGet_returnsCachedNodes() {
		Node node = new TestNode();
		Map<String, Node> nodes = Map.of("tmpl", node);

		cache.put("key1", nodes);
		Map<String, Node> result = cache.get("key1");

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(node, result.get("tmpl"));
	}

	@Test
	void get_returnsNullOnMiss() {
		assertNull(cache.get("nonexistent"));
	}

	@Test
	void lruEviction_removesOldestEntryWhenFull() {
		TemplateCache smallCache = new TemplateCache(2);
		Node node = new TestNode();

		smallCache.put("a", Map.of("n", node));
		smallCache.put("b", Map.of("n", node));
		// Access "a" to make "b" the least-recently-used
		smallCache.get("a");
		// Adding "c" should evict "b"
		smallCache.put("c", Map.of("n", node));

		assertNotNull(smallCache.get("a"));
		assertNull(smallCache.get("b"));
		assertNotNull(smallCache.get("c"));
		assertEquals(2, smallCache.size());
	}

	@Test
	void clear_emptiesCache() {
		Node node = new TestNode();
		cache.put("k1", Map.of("n", node));
		cache.put("k2", Map.of("n", node));

		cache.clear();

		assertEquals(0, cache.size());
		assertNull(cache.get("k1"));
	}

	@Test
	void put_storesDefensiveCopy() {
		Node node = new TestNode();
		Map<String, Node> original = new HashMap<>();
		original.put("tmpl", node);

		cache.put("key", original);
		// Mutate the original map — the cache should not be affected
		original.put("extra", node);

		Map<String, Node> cached = cache.get("key");
		assertNotNull(cached);
		assertEquals(1, cached.size());
		assertNotSame(original, cached);
	}

	// Minimal Node implementation for testing
	private static final class TestNode implements Node {

	}

}
