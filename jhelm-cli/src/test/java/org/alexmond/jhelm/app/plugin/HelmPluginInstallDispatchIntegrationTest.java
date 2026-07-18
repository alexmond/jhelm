package org.alexmond.jhelm.app.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that a plugin installed by {@link HelmPluginInstaller} is then found
 * and run by {@link HelmPluginDispatcher} — the two agree on the {@code $HELM_PLUGINS}
 * layout and the plugin's resolved command, exercised through the real filesystem and a
 * real subprocess.
 */
@EnabledOnOs({ OS.LINUX, OS.MAC })
class HelmPluginInstallDispatchIntegrationTest {

	@TempDir
	Path pluginsDir;

	@TempDir
	Path source;

	private HelmPluginPaths paths() {
		return new HelmPluginPaths(Map.of("HELM_PLUGINS", this.pluginsDir.toString())::get, Path.of("/home/tester"));
	}

	private Supplier<HelmPluginEnvironment> env() {
		return () -> HelmPluginEnvironment.builder().paths(paths()).namespace("it-ns").build();
	}

	private JhelmSecurityPolicy fullPolicy() {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(JhelmAccessMode.FULL);
		return new JhelmSecurityPolicy(props);
	}

	@Test
	void installedSubcommandPluginIsDispatchable() throws Exception {
		// A plugin source directory with a script that records its args + HELM_* env.
		Path pluginSource = Files.createDirectories(this.source.resolve("greet"));
		Files.writeString(pluginSource.resolve("plugin.yaml"), """
				name: greet
				version: 1.0.0
				usage: "greet plugin"
				command: "$HELM_PLUGIN_DIR/greet.sh"
				""");
		Path script = pluginSource.resolve("greet.sh");
		Files.writeString(script, """
				#!/usr/bin/env bash
				out="$1"; shift
				{ echo "args:$*"; echo "ns:$HELM_NAMESPACE"; echo "dir:$HELM_PLUGIN_DIR"; } > "$out"
				""");
		script.toFile().setExecutable(true);

		HelmPluginInstaller installer = new HelmPluginInstaller(paths(), env(), fullPolicy(),
				new ProcessHelmPluginRunner(), (u, r, d) -> {
				});
		HelmPluginDispatcher dispatcher = HelmPluginDispatcher.forTesting(this.pluginsDir, env(), fullPolicy(),
				new ProcessHelmPluginRunner());

		// Install, then dispatch the just-installed plugin.
		DiscoveredHelmPlugin installed = installer.install(pluginSource.toString(), null);
		assertEquals("greet", installed.name());

		Path out = this.pluginsDir.resolve("out.txt");
		DiscoveredHelmPlugin found = dispatcher.find("greet").orElseThrow();
		int code = dispatcher.dispatch(found, List.of(out.toString(), "hello", "world"));

		assertEquals(0, code);
		Set<String> lines = Set.copyOf(Files.readAllLines(out));
		assertTrue(lines.contains("args:hello world"), lines.toString());
		assertTrue(lines.contains("ns:it-ns"));
		assertTrue(lines.contains("dir:" + this.pluginsDir.resolve("greet")));
	}

}
