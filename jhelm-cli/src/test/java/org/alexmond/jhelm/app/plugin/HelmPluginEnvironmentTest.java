package org.alexmond.jhelm.app.plugin;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HelmPluginEnvironmentTest {

	private HelmPluginEnvironment environment() {
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", "/plugins")::get, Path.of("/home/tester"));
		return HelmPluginEnvironment.builder()
			.paths(paths)
			.namespace("apps")
			.kubeContext("prod")
			.kubeConfig("/home/tester/.kube/config")
			.repositoryCache("/home/tester/.cache/helm/repository")
			.debug(true)
			.build();
	}

	@Test
	void baseEnvironmentExportsHelmDirectoriesAndContext() {
		Map<String, String> env = environment().base();
		assertEquals("jhelm", env.get("HELM_BIN"));
		assertEquals("/plugins", env.get("HELM_PLUGINS"));
		assertEquals("/home/tester/.local/share/helm", env.get("HELM_DATA_HOME"));
		assertEquals("apps", env.get("HELM_NAMESPACE"));
		assertEquals("prod", env.get("HELM_KUBECONTEXT"));
		assertEquals("/home/tester/.kube/config", env.get("KUBECONFIG"));
		assertEquals("true", env.get("HELM_DEBUG"));
	}

	@Test
	void blankOptionalValuesAreOmitted() {
		Map<String, String> env = environment().base();
		assertFalse(env.containsKey("HELM_KUBEAPISERVER"));
		assertFalse(env.containsKey("HELM_REGISTRY_CONFIG"));
	}

	@Test
	void forPluginAddsPluginNameAndDir() {
		HelmPluginManifest manifest = new HelmPluginManifest();
		manifest.setName("diff");
		DiscoveredHelmPlugin plugin = new DiscoveredHelmPlugin("diff", Path.of("/plugins/diff"), manifest);
		Map<String, String> env = environment().forPlugin(plugin);
		assertEquals("diff", env.get("HELM_PLUGIN_NAME"));
		assertEquals("/plugins/diff", env.get("HELM_PLUGIN_DIR"));
		assertEquals("/plugins", env.get("HELM_PLUGINS"));
	}

}
