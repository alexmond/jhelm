package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmPostRendererResolverTest {

	@TempDir
	Path pluginsDir;

	@TempDir
	Path work;

	private HelmPostRendererResolver resolver(JhelmAccessMode mode) {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(mode);
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", this.pluginsDir.toString())::get,
				Path.of("/home/tester"));
		Supplier<HelmPluginEnvironment> env = () -> HelmPluginEnvironment.builder().paths(paths).build();
		return new HelmPostRendererResolver(paths, env, new JhelmSecurityPolicy(props));
	}

	@Test
	void passesAnExistingFileThroughWithArgs() throws Exception {
		Path exe = Files.writeString(this.work.resolve("kustomize.sh"), "#!/bin/sh\n");
		List<String> command = resolver(JhelmAccessMode.FULL).resolve(exe.toString(), List.of("--enable-alpha"));
		assertEquals(List.of(exe.toString(), "--enable-alpha"), command);
	}

	@Test
	void resolvesAnInstalledPluginNameToItsCommand() throws Exception {
		Path dir = Files.createDirectories(this.pluginsDir.resolve("kustomize"));
		Files.writeString(dir.resolve("plugin.yaml"), "name: kustomize\ncommand: \"$HELM_PLUGIN_DIR/bin/render\"\n");
		List<String> command = resolver(JhelmAccessMode.FULL).resolve("kustomize", List.of("build"));
		assertEquals(List.of(dir.resolve("bin/render").toString(), "build"), command);
	}

	@Test
	void readOnlyModeRefusesAPluginPostRenderer() throws Exception {
		Path dir = Files.createDirectories(this.pluginsDir.resolve("kustomize"));
		Files.writeString(dir.resolve("plugin.yaml"), "name: kustomize\ncommand: run\n");
		IOException ex = assertThrows(IOException.class,
				() -> resolver(JhelmAccessMode.READ_ONLY).resolve("kustomize", List.of()));
		assertTrue(ex.getMessage().contains("READ_ONLY"));
	}

	@Test
	void passesAnUnknownValueThroughVerbatim() throws Exception {
		List<String> command = resolver(JhelmAccessMode.FULL).resolve("/usr/local/bin/some-renderer", List.of("-x"));
		assertEquals(List.of("/usr/local/bin/some-renderer", "-x"), command);
	}

}
