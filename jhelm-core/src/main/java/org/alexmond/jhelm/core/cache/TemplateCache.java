package org.alexmond.jhelm.core.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.gotemplate.internal.parse.Node;

/**
 * LRU cache for parsed template ASTs. Keyed by template name and content hash to avoid
 * re-parsing identical template content across multiple render calls.
 */
@Slf4j
public final class TemplateCache {

	private final Map<String, Map<String, Node>> cache;

	private final int maxSize;

	private final JhelmMetrics metrics;

	public TemplateCache(int maxSize) {
		this(maxSize, null);
	}

	public TemplateCache(int maxSize, JhelmMetrics metrics) {
		this.maxSize = maxSize;
		this.metrics = metrics;
		this.cache = Collections.synchronizedMap(new LinkedHashMap<>(maxSize, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Map<String, Node>> eldest) {
				return size() > TemplateCache.this.maxSize;
			}
		});
		if (metrics != null) {
			metrics.registerCacheSizeGauge(this::size);
		}
	}

	/**
	 * Look up cached nodes for the given key.
	 * @param key the cache key
	 * @return the cached node map, or {@code null} on a miss
	 */
	public Map<String, Node> get(String key) {
		Map<String, Node> cached = cache.get(key);
		if (cached != null) {
			log.debug("Template cache hit for key: {}", key);
			if (metrics != null) {
				metrics.recordCacheHit();
			}
		}
		else {
			if (metrics != null) {
				metrics.recordCacheMiss();
			}
		}
		return cached;
	}

	/**
	 * Store a defensive copy of the given nodes under the given key.
	 * @param key the cache key
	 * @param nodes the nodes to cache
	 */
	public void put(String key, Map<String, Node> nodes) {
		cache.put(key, new HashMap<>(nodes));
	}

	/**
	 * Return the number of entries currently in the cache.
	 */
	public int size() {
		return cache.size();
	}

	/**
	 * Clear all cached entries.
	 */
	public void clear() {
		cache.clear();
	}

}
