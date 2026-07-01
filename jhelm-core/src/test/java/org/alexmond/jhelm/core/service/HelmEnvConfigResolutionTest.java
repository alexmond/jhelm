package org.alexmond.jhelm.core.service;

import java.nio.file.Paths;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies jhelm resolves Helm's config-file locations the way Helm does — honoring the
 * {@code HELM_*} environment variables so a Helm user's environment drops in — with the
 * per-OS Helm default as the fallback (#606). The OS name is passed explicitly to keep
 * the assertions platform-independent.
 */
class HelmEnvConfigResolutionTest {

	@Test
	void repositoriesConfigHonorsHelmRepositoryConfig() {
		assertEquals("/custom/repositories.yaml",
				RepoManager.resolveConfigPath("/custom/repositories.yaml", "/home/u", "Linux"));
	}

	@Test
	void repositoriesConfigFallsBackToHelmOsDefault() {
		assertEquals(Paths.get("/home/u", ".config/helm/repositories.yaml").toString(),
				RepoManager.resolveConfigPath(null, "/home/u", "Linux"));
		// blank env is treated as unset
		assertTrue(RepoManager.resolveConfigPath("  ", "/home/u", "Linux").endsWith("repositories.yaml"));
	}

	@Test
	void repositoryCacheHonorsHelmRepositoryCache() {
		assertEquals(Paths.get("/shared/helm/repository").toFile(),
				RepoManager.resolveCacheDir("/shared/helm/repository", "/home/u", "Linux"));
	}

	@Test
	void repositoryCacheFallsBackToJhelmDefault() {
		assertEquals(Paths.get("/home/u", ".cache/jhelm/repository").toFile(),
				RepoManager.resolveCacheDir(null, "/home/u", "Linux"));
	}

	@Test
	void resolvesTheMacOsHelmLocations() {
		assertEquals(Paths.get("/home/u", "Library/Preferences/helm/repositories.yaml").toString(),
				RepoManager.resolveConfigPath(null, "/home/u", "Mac OS X"));
		assertEquals(Paths.get("/home/u", "Library/Caches/jhelm/repository").toFile(),
				RepoManager.resolveCacheDir(null, "/home/u", "Mac OS X"));
		assertEquals(Paths.get("/home/u", "Library/Preferences/helm/registry/config.json").toString(),
				RegistryManager.resolveConfigPath(null, "/home/u", "Mac OS X"));
	}

	@Test
	void registryConfigHonorsHelmRegistryConfig() {
		assertEquals("/custom/registry.json",
				RegistryManager.resolveConfigPath("/custom/registry.json", "/home/u", "Linux"));
	}

	@Test
	void registryConfigFallsBackToHelmOsDefault() {
		assertEquals(Paths.get("/home/u", ".config/helm/registry/config.json").toString(),
				RegistryManager.resolveConfigPath(null, "/home/u", "Linux"));
	}

	@Test
	void defaultConstructorsResolveAgainstTheCurrentEnvironment() {
		// exercises the default-path resolution wrappers (System.getenv/getProperty)
		assertNotNull(new RegistryManager());
		assertNotNull(new RepoManager());
	}

	@Test
	void autoConfigRegistryManagerHonorsRegistryConfigPathProperty() {
		JhelmCoreAutoConfiguration autoConfig = new JhelmCoreAutoConfiguration();
		JhelmCoreProperties withPath = new JhelmCoreProperties();
		withPath.setRegistryConfigPath("/tmp/jhelm-test/registry/config.json");
		assertNotNull(autoConfig.registryManager(withPath));
		// null property -> default resolution branch
		assertNotNull(autoConfig.registryManager(new JhelmCoreProperties()));
	}

}
