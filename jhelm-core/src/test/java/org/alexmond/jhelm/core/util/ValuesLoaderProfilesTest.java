package org.alexmond.jhelm.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests profile resolution in {@link ValuesLoader}: {@code on-profile} document gating,
 * directive-key stripping, and {@code -<profile>} sidecar files.
 */
class ValuesLoaderProfilesTest {

	@TempDir
	File dir;

	private File write(String name, String content) throws IOException {
		File f = new File(dir, name);
		Files.writeString(f.toPath(), content);
		return f;
	}

	@Test
	void testOnProfileGatingAndStripping() throws IOException {
		File values = write("values.yaml", """
				replicas: 1
				---
				spring.config.activate.on-profile: prod
				replicas: 3
				""");

		Map<String, Object> base = ValuesLoader.load(values, ValuesProfiles.none());
		assertEquals(1, base.get("replicas"), "prod document must not apply when prod is inactive");
		assertNull(base.get("spring.config.activate.on-profile"), "directive key must be stripped");
		assertFalse(base.containsKey("spring"), "no nested directive residue");

		Map<String, Object> prod = ValuesLoader.load(values, ValuesProfiles.of(List.of("prod")));
		assertEquals(3, prod.get("replicas"), "prod document applies when prod is active");
		assertNull(prod.get("spring.config.activate.on-profile"));
	}

	@Test
	void testNestedDirectiveFormGatesAndPrunes() throws IOException {
		File values = write("values.yaml", """
				replicas: 1
				---
				spring:
				  config:
				    activate:
				      on-profile: prod
				replicas: 5
				""");
		Map<String, Object> prod = ValuesLoader.load(values, ValuesProfiles.of(List.of("prod")));
		assertEquals(5, prod.get("replicas"));
		assertFalse(prod.containsKey("spring"), "nested directive tree must be pruned, not leaked into .Values");
	}

	@Test
	void testOnCloudPlatformKeyStrippedButDocumentApplies() throws IOException {
		// jhelm does not gate on on-cloud-platform, but must strip it so it never leaks.
		File values = write("values.yaml", """
				replicas: 1
				---
				spring.config.activate.on-cloud-platform: kubernetes
				extra: yes
				""");
		Map<String, Object> out = ValuesLoader.load(values, ValuesProfiles.none());
		assertEquals(Boolean.TRUE, out.get("extra"), "on-cloud-platform document applies (not gated)");
		assertNull(out.get("spring.config.activate.on-cloud-platform"), "on-cloud-platform key stripped");
	}

	@Test
	void testSidecarFileMergedWhenProfileActive() throws IOException {
		File values = write("values.yaml", """
				replicas: 1
				image:
				  tag: base
				""");
		write("values-prod.yaml", """
				replicas: 4
				image:
				  tag: prod
				""");

		Map<String, Object> none = ValuesLoader.load(values, ValuesProfiles.none());
		assertEquals(1, none.get("replicas"), "sidecar not applied without the profile");

		Map<String, Object> prod = ValuesLoader.load(values, ValuesProfiles.of(List.of("prod")));
		assertEquals(4, prod.get("replicas"), "sidecar overrides base when prod active");
		assertEquals("prod", ((Map<?, ?>) prod.get("image")).get("tag"), "deep-merge with sidecar");
	}

	@Test
	void testMultipleProfilesLastWinsForSidecars() throws IOException {
		File values = write("values.yaml", "v: base\n");
		write("values-a.yaml", "v: a\n");
		write("values-b.yaml", "v: b\n");
		// [a, b] -> b applied after a -> b wins
		assertEquals("b", ValuesLoader.load(values, ValuesProfiles.of(List.of("a", "b"))).get("v"));
		assertEquals("a", ValuesLoader.load(values, ValuesProfiles.of(List.of("b", "a"))).get("v"));
	}

	@Test
	void testPlainMultiDocFileUnchangedWithoutProfiles() throws IOException {
		// Regression: a file with no directive merges identically under none()
		// (byte-for-byte
		// behaviour preserved for the helm-parity suite).
		File values = write("values.yaml", """
				a: 1
				---
				b: 2
				""");
		Map<String, Object> out = ValuesLoader.load(values, ValuesProfiles.none());
		assertEquals(1, out.get("a"));
		assertEquals(2, out.get("b"));
		assertTrue(ValuesLoader.load(values).equals(out), "no-arg load matches none()");
	}

}
