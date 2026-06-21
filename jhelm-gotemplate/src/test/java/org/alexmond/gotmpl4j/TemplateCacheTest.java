package org.alexmond.gotmpl4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateCacheTest {

	private static Supplier<GoTemplate> compiler(AtomicInteger compiles) {
		return () -> {
			compiles.incrementAndGet();
			return new GoTemplate();
		};
	}

	@Test
	void enabledCompilesOnceAndReusesResult() {
		AtomicInteger compiles = new AtomicInteger();
		TemplateCache cache = new TemplateCache(compiler(compiles));

		GoTemplate first = cache.get();
		GoTemplate second = cache.get();

		assertSame(first, second);
		assertEquals(1, compiles.get());
		assertTrue(cache.isEnabled());
	}

	@Test
	void disabledRecompilesEveryCall() {
		AtomicInteger compiles = new AtomicInteger();
		TemplateCache cache = new TemplateCache(compiler(compiles), false);

		GoTemplate first = cache.get();
		GoTemplate second = cache.get();

		assertNotSame(first, second);
		assertEquals(2, compiles.get());
	}

	@Test
	void invalidateForcesRecompile() {
		AtomicInteger compiles = new AtomicInteger();
		TemplateCache cache = new TemplateCache(compiler(compiles));

		GoTemplate first = cache.get();
		cache.invalidate();
		GoTemplate second = cache.get();

		assertNotSame(first, second);
		assertEquals(2, compiles.get());
	}

	@Test
	void nullCompilerRejected() {
		assertThrows(IllegalArgumentException.class, () -> new TemplateCache(null));
	}

}
