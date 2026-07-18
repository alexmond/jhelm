package org.alexmond.jhelm.app.plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveredHelmPluginTest {

	private static DiscoveredHelmPlugin plugin(HelmPluginManifest manifest) {
		return new DiscoveredHelmPlugin("diff", Path.of("/plugins/diff"), manifest);
	}

	private static HelmPluginManifest manifest(String command) {
		HelmPluginManifest manifest = new HelmPluginManifest();
		manifest.setName("diff");
		manifest.setCommand(command);
		return manifest;
	}

	private static HelmPluginManifest.PlatformCommand platform(String os, String arch, String command) {
		HelmPluginManifest.PlatformCommand pc = new HelmPluginManifest.PlatformCommand();
		pc.setOs(os);
		pc.setArch(arch);
		pc.setCommand(command);
		return pc;
	}

	@Test
	void expandsPluginDirAndSplitsArgv() {
		Map<String, String> env = Map.of("HELM_PLUGIN_DIR", "/plugins/diff");
		List<String> argv = plugin(manifest("$HELM_PLUGIN_DIR/bin/diff --detailed")).resolveCommand("linux", "amd64",
				env);
		assertEquals(List.of("/plugins/diff/bin/diff", "--detailed"), argv);
	}

	@Test
	void selectsExactPlatformCommandFirst() {
		HelmPluginManifest manifest = manifest("fallback");
		manifest.setPlatformCommand(List.of(platform("linux", null, "linux-generic"),
				platform("linux", "amd64", "linux-amd64"), platform("darwin", "arm64", "darwin-arm64")));
		assertEquals(List.of("linux-amd64"), plugin(manifest).resolveCommand("linux", "amd64", Map.of()));
	}

	@Test
	void fallsBackToOsOnlyThenTopLevelCommand() {
		HelmPluginManifest manifest = manifest("fallback");
		manifest.setPlatformCommand(List.of(platform("linux", null, "linux-generic")));
		assertEquals(List.of("linux-generic"), plugin(manifest).resolveCommand("linux", "arm64", Map.of()));
		assertEquals(List.of("fallback"), plugin(manifest).resolveCommand("windows", "amd64", Map.of()));
	}

	@Test
	void honorsBracedVarsAndQuotedArgs() {
		Map<String, String> env = Map.of("HELM_PLUGIN_DIR", "/p");
		List<String> argv = plugin(manifest("${HELM_PLUGIN_DIR}/run \"one two\" three")).resolveCommand("linux",
				"amd64", env);
		assertEquals(List.of("/p/run", "one two", "three"), argv);
	}

	@Test
	void unsetVariableExpandsToEmpty() {
		assertEquals(List.of("/bin/x"), plugin(manifest("$MISSING/bin/x")).resolveCommand("linux", "amd64", Map.of()));
	}

	@Test
	void emptyCommandYieldsEmptyList() {
		assertEquals(List.of(), plugin(manifest(null)).resolveCommand("linux", "amd64", Map.of()));
	}

}
