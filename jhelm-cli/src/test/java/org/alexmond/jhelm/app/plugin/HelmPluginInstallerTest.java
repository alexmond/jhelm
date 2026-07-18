package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmPluginInstallerTest {

	@TempDir
	Path pluginsDir;

	@TempDir
	Path work;

	private HelmPluginInstaller installer(JhelmAccessMode mode, GitCloner cloner) {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(mode);
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", this.pluginsDir.toString())::get,
				Path.of("/home/tester"));
		Supplier<HelmPluginEnvironment> env = () -> HelmPluginEnvironment.builder().paths(paths).build();
		return new HelmPluginInstaller(paths, env, new JhelmSecurityPolicy(props), new ProcessHelmPluginRunner(),
				cloner);
	}

	private Path pluginSourceDir(String name, String extraYaml) throws IOException {
		Path dir = Files.createDirectories(this.work.resolve(name + "-src"));
		Files.writeString(dir.resolve("plugin.yaml"), "name: " + name + "\nversion: 1.2.3\ncommand: run\n" + extraYaml);
		return dir;
	}

	@Test
	void installsFromLocalDirectory() throws Exception {
		Path src = pluginSourceDir("mydiff", "");
		HelmPluginInstaller installer = installer(JhelmAccessMode.FULL, failCloner());

		DiscoveredHelmPlugin plugin = installer.install(src.toString(), null);

		assertEquals("mydiff", plugin.name());
		assertTrue(Files.isRegularFile(this.pluginsDir.resolve("mydiff/plugin.yaml")));
		assertEquals("1.2.3", plugin.manifest().getVersion());
	}

	@Test
	void installsFromLocalTarball() throws Exception {
		Path src = pluginSourceDir("tarplugin", "");
		Path tgz = this.work.resolve("tarplugin.tar.gz");
		makeTarGz(src, tgz);
		HelmPluginInstaller installer = installer(JhelmAccessMode.FULL, failCloner());

		DiscoveredHelmPlugin plugin = installer.install(tgz.toString(), null);

		assertEquals("tarplugin", plugin.name());
		assertTrue(Files.isRegularFile(this.pluginsDir.resolve("tarplugin/plugin.yaml")));
	}

	@Test
	void installsFromGitViaCloner() throws Exception {
		Path src = pluginSourceDir("gitplugin", "");
		GitCloner cloner = (url, ref, dest) -> FileUtils.copyDirectory(src.toFile(), dest.toFile());
		HelmPluginInstaller installer = installer(JhelmAccessMode.FULL, cloner);

		DiscoveredHelmPlugin plugin = installer.install("https://github.com/example/gitplugin.git", "v1");

		assertEquals("gitplugin", plugin.name());
		assertTrue(Files.isRegularFile(this.pluginsDir.resolve("gitplugin/plugin.yaml")));
	}

	@Test
	void refusesToInstallOverExistingPlugin() throws Exception {
		Path src = pluginSourceDir("dup", "");
		installer(JhelmAccessMode.FULL, failCloner()).install(src.toString(), null);
		IOException ex = assertThrows(IOException.class,
				() -> installer(JhelmAccessMode.FULL, failCloner()).install(src.toString(), null));
		assertTrue(ex.getMessage().contains("already installed"));
	}

	@Test
	void rejectsUnrecognizedSource() {
		assertThrows(IOException.class,
				() -> installer(JhelmAccessMode.FULL, failCloner()).install("not-a-source", null));
	}

	@Test
	void rejectsGitRefThatSmugglesAFlag() {
		IOException ex = assertThrows(IOException.class, () -> installer(JhelmAccessMode.FULL, failCloner())
			.install("git://example.com/x.git", "--upload-pack=touch /tmp/pwned"));
		assertTrue(ex.getMessage().contains("invalid git ref"));
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	void runsInstallHookInFullMode() throws Exception {
		Path marker = this.work.resolve("hook-ran");
		Path src = pluginSourceDir("hooked", "hooks:\n  install: \"touch " + marker + "\"\n");
		installer(JhelmAccessMode.FULL, failCloner()).install(src.toString(), null);
		assertTrue(Files.exists(marker), "install hook should run in FULL mode");
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	void skipsInstallHookInReadOnlyMode() throws Exception {
		Path marker = this.work.resolve("hook-skipped");
		Path src = pluginSourceDir("hooked", "hooks:\n  install: \"touch " + marker + "\"\n");
		DiscoveredHelmPlugin plugin = installer(JhelmAccessMode.READ_ONLY, failCloner()).install(src.toString(), null);
		assertTrue(Files.isRegularFile(this.pluginsDir.resolve("hooked/plugin.yaml")), "files still installed");
		assertFalse(Files.exists(marker), "install hook must not run in READ_ONLY mode");
		assertEquals("hooked", plugin.name());
	}

	@Test
	void listReturnsInstalledPlugins() throws Exception {
		installer(JhelmAccessMode.FULL, failCloner()).install(pluginSourceDir("aaa", "").toString(), null);
		installer(JhelmAccessMode.FULL, failCloner()).install(pluginSourceDir("bbb", "").toString(), null);
		List<DiscoveredHelmPlugin> list = installer(JhelmAccessMode.FULL, failCloner()).list();
		assertEquals(List.of("aaa", "bbb"), list.stream().map(DiscoveredHelmPlugin::name).toList());
	}

	@Test
	void uninstallRemovesThePluginDirectory() throws Exception {
		installer(JhelmAccessMode.FULL, failCloner()).install(pluginSourceDir("gone", "").toString(), null);
		assertTrue(installer(JhelmAccessMode.FULL, failCloner()).uninstall("gone"));
		assertFalse(Files.exists(this.pluginsDir.resolve("gone")));
		assertFalse(installer(JhelmAccessMode.FULL, failCloner()).uninstall("gone"), "second uninstall finds nothing");
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	void uninstallRunsDeleteHook() throws Exception {
		Path marker = this.work.resolve("deleted");
		installer(JhelmAccessMode.FULL, failCloner())
			.install(pluginSourceDir("hd", "hooks:\n  delete: \"touch " + marker + "\"\n").toString(), null);
		installer(JhelmAccessMode.FULL, failCloner()).uninstall("hd");
		assertTrue(Files.exists(marker), "delete hook should run");
		assertFalse(Files.exists(this.pluginsDir.resolve("hd")), "directory removed");
	}

	@Test
	void updateGitPluginPullsThroughTheUpdater() throws Exception {
		installer(JhelmAccessMode.FULL, failCloner()).install(pluginSourceDir("gp", "").toString(), null);
		Files.createDirectories(this.pluginsDir.resolve("gp/.git"));
		AtomicBoolean pulled = new AtomicBoolean();
		HelmPluginInstaller inst = installer6(JhelmAccessMode.FULL, (dir) -> pulled.set(true));
		assertTrue(inst.update("gp").isPresent());
		assertTrue(pulled.get(), "git-checked-out plugin should be pulled");
	}

	@Test
	void updateReturnsEmptyForUnknownPlugin() throws Exception {
		assertTrue(installer(JhelmAccessMode.FULL, failCloner()).update("nope").isEmpty());
	}

	private HelmPluginInstaller installer6(JhelmAccessMode mode, GitUpdater updater) {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(mode);
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", this.pluginsDir.toString())::get,
				Path.of("/home/tester"));
		Supplier<HelmPluginEnvironment> env = () -> HelmPluginEnvironment.builder().paths(paths).build();
		return new HelmPluginInstaller(paths, env, new JhelmSecurityPolicy(props), new ProcessHelmPluginRunner(),
				failCloner(), updater);
	}

	private static GitCloner failCloner() {
		return (url, ref, dest) -> {
			throw new AssertionError("git cloner should not be used");
		};
	}

	private static void makeTarGz(Path sourceDir, Path target) throws IOException {
		try (OutputStream fileOut = Files.newOutputStream(target);
				GZIPOutputStream gzip = new GZIPOutputStream(fileOut);
				TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip);
				Stream<Path> tree = Files.walk(sourceDir)) {
			tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			for (Path path : tree.filter(Files::isRegularFile).toList()) {
				String name = sourceDir.getFileName() + "/" + sourceDir.relativize(path);
				TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), name);
				tar.putArchiveEntry(entry);
				Files.copy(path, tar);
				tar.closeArchiveEntry();
			}
		}
	}

}
