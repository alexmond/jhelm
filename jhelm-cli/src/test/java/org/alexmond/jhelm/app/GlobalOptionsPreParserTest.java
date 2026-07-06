package org.alexmond.jhelm.app;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalOptionsPreParserTest {

	@Test
	void mapsSpaceSeparatedValueFlagsAndStripsThem() {
		GlobalOptionsPreParser.Result r = GlobalOptionsPreParser
			.parse(new String[] { "install", "foo", "--kubeconfig", "/k", "--repository-config", "/r" });

		assertEquals("/k", r.systemProperties().get("jhelm.kubernetes.kubeconfig-path"));
		assertEquals("/r", r.systemProperties().get("jhelm.config-path"));
		assertArrayEquals(new String[] { "install", "foo" }, r.springArgs());
	}

	@Test
	void mapsInlineValueFlags() {
		GlobalOptionsPreParser.Result r = GlobalOptionsPreParser.parse(new String[] { "--kube-context=prod", "list" });

		assertEquals("prod", r.systemProperties().get("jhelm.kubernetes.context"));
		assertArrayEquals(new String[] { "list" }, r.springArgs());
	}

	@Test
	void debugMapsToLogLevelAndIsStripped() {
		GlobalOptionsPreParser.Result r = GlobalOptionsPreParser.parse(new String[] { "--debug", "status", "rel" });

		assertEquals("DEBUG", r.systemProperties().get("logging.level.org.alexmond.jhelm"));
		assertArrayEquals(new String[] { "status", "rel" }, r.springArgs());
	}

	@Test
	void nonGlobalArgsArePreservedUntouched() {
		String[] args = { "template", "./chart", "--set", "a=b" };
		GlobalOptionsPreParser.Result r = GlobalOptionsPreParser.parse(args);

		assertTrue(r.systemProperties().isEmpty());
		assertArrayEquals(args, r.springArgs());
	}

	@Test
	void mapsEveryGlobalFlag() {
		GlobalOptionsPreParser.Result r = GlobalOptionsPreParser.parse(new String[] { "--kubeconfig", "/k",
				"--kube-context", "c", "--kube-apiserver", "https://s:6443", "--registry-config", "/rc",
				"--repository-config", "/rp", "--repository-cache", "/rch", "--debug", "version" });

		Map<String, String> p = r.systemProperties();
		assertEquals("/k", p.get("jhelm.kubernetes.kubeconfig-path"));
		assertEquals("c", p.get("jhelm.kubernetes.context"));
		assertEquals("https://s:6443", p.get("jhelm.kubernetes.api-server"));
		assertEquals("/rc", p.get("jhelm.registry-config-path"));
		assertEquals("/rp", p.get("jhelm.config-path"));
		assertEquals("/rch", p.get("jhelm.repository-cache-path"));
		assertEquals("DEBUG", p.get("logging.level.org.alexmond.jhelm"));
		assertArrayEquals(new String[] { "version" }, r.springArgs());
	}

}
