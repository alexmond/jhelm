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

import picocli.CommandLine.ExitCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs({ OS.LINUX, OS.MAC })
class HelmPluginDispatcherTest {

	@TempDir
	Path pluginsDir;

	private Path installPlugin(String name, String script) throws Exception {
		Path dir = Files.createDirectories(this.pluginsDir.resolve(name));
		Files.writeString(dir.resolve("plugin.yaml"),
				"name: " + name + "\nversion: 1.0.0\ncommand: \"$HELM_PLUGIN_DIR/run.sh\"\n");
		Path run = dir.resolve("run.sh");
		Files.writeString(run, script);
		run.toFile().setExecutable(true);
		return dir;
	}

	private Supplier<HelmPluginEnvironment> env() {
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", this.pluginsDir.toString())::get,
				Path.of("/home/tester"));
		return () -> HelmPluginEnvironment.builder().paths(paths).namespace("testns").build();
	}

	private HelmPluginDispatcher dispatcher(JhelmAccessMode mode) {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(mode);
		return HelmPluginDispatcher.forTesting(this.pluginsDir, env(), new JhelmSecurityPolicy(props),
				new ProcessHelmPluginRunner());
	}

	@Test
	void runsPluginForwardingArgsAndHelmEnv() throws Exception {
		installPlugin("echoargs", """
				#!/usr/bin/env bash
				out="$1"; shift
				{
				  echo "args:$*"
				  echo "HELM_NAMESPACE=$HELM_NAMESPACE"
				  echo "HELM_PLUGIN_NAME=$HELM_PLUGIN_NAME"
				  echo "HELM_PLUGIN_DIR=$HELM_PLUGIN_DIR"
				} > "$out"
				""");
		Path out = this.pluginsDir.resolve("out.txt");
		HelmPluginDispatcher dispatcher = dispatcher(JhelmAccessMode.FULL);
		DiscoveredHelmPlugin plugin = dispatcher.find("echoargs").orElseThrow();

		int code = dispatcher.dispatch(plugin, List.of(out.toString(), "hello", "world"));

		assertEquals(0, code);
		Set<String> lines = Set.copyOf(Files.readAllLines(out));
		assertTrue(lines.contains("args:hello world"), lines.toString());
		assertTrue(lines.contains("HELM_NAMESPACE=testns"));
		assertTrue(lines.contains("HELM_PLUGIN_NAME=echoargs"));
		assertTrue(lines.contains("HELM_PLUGIN_DIR=" + plugin.directory()));
	}

	@Test
	void propagatesPluginExitCode() throws Exception {
		installPlugin("failing", """
				#!/usr/bin/env bash
				exit 7
				""");
		HelmPluginDispatcher dispatcher = dispatcher(JhelmAccessMode.FULL);
		int code = dispatcher.dispatch(dispatcher.find("failing").orElseThrow(), List.of());
		assertEquals(7, code);
	}

	@Test
	void readOnlyModeBlocksExecution() throws Exception {
		installPlugin("echoargs", """
				#!/usr/bin/env bash
				touch "$1"
				""");
		Path marker = this.pluginsDir.resolve("ran.marker");
		HelmPluginDispatcher dispatcher = dispatcher(JhelmAccessMode.READ_ONLY);
		int code = dispatcher.dispatch(dispatcher.find("echoargs").orElseThrow(), List.of(marker.toString()));
		assertEquals(ExitCode.SOFTWARE, code);
		assertFalse(Files.exists(marker), "plugin must not run in READ_ONLY mode");
	}

	@Test
	void findReturnsEmptyForUnknownPlugin() {
		assertTrue(dispatcher(JhelmAccessMode.FULL).find("nope").isEmpty());
	}

}
