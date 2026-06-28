package org.alexmond.jhelm.core.model;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseContextTest {

	@Test
	void testToMapProducesExactReleaseWireForm() {
		ReleaseContext release = ReleaseContext.builder()
			.name("my-release")
			.namespace("prod")
			.install(true)
			.upgrade(false)
			.revision(7)
			.build();

		Map<String, Object> map = release.toMap();

		assertEquals("my-release", map.get("Name"));
		assertEquals("prod", map.get("Namespace"));
		assertEquals("Helm", map.get("Service"));
		assertEquals(true, map.get("IsInstall"));
		assertEquals(false, map.get("IsUpgrade"));
		assertEquals(7, map.get("Revision"));
	}

	@Test
	void testServiceDefaultsToHelm() {
		ReleaseContext release = ReleaseContext.builder().name("r").namespace("ns").build();

		assertEquals("Helm", release.getService());
		assertEquals("Helm", release.toMap().get("Service"));
	}

	@Test
	void testToMapHasExactKeysAndTypes() {
		ReleaseContext release = ReleaseContext.builder()
			.name("r")
			.namespace("ns")
			.install(false)
			.upgrade(true)
			.revision(1)
			.build();

		Map<String, Object> map = release.toMap();

		assertEquals(6, map.size());
		assertTrue(map.containsKey("Name"));
		assertTrue(map.containsKey("Namespace"));
		assertTrue(map.containsKey("Service"));
		assertTrue(map.containsKey("IsInstall"));
		assertTrue(map.containsKey("IsUpgrade"));
		assertTrue(map.containsKey("Revision"));

		// Types must match the hand-built maps the engine historically consumed.
		assertInstanceOf(Boolean.class, map.get("IsInstall"));
		assertInstanceOf(Boolean.class, map.get("IsUpgrade"));
		assertInstanceOf(Integer.class, map.get("Revision"));
	}

	@Test
	void testToMapPreservesInsertionOrder() {
		ReleaseContext release = ReleaseContext.builder().name("r").namespace("ns").revision(1).build();

		assertEquals(List.of("Name", "Namespace", "Service", "IsInstall", "IsUpgrade", "Revision"),
				List.copyOf(release.toMap().keySet()));
	}

}
