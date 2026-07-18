package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Installs a Helm plugin from a Helm-style source — a git repository URL, a
 * {@code .tar.gz}/{@code .tgz} archive (local file or {@code http(s)} URL), or a local
 * directory — into the Helm plugins directory, then runs the plugin's {@code install}
 * hook. Mirrors {@code helm plugin install}: the plugin lands under
 * {@code $HELM_PLUGINS/<name>/}, keyed on the {@code name} from its {@code plugin.yaml}.
 *
 * <p>
 * Copying files is inert and runs in any posture; running the {@code install} hook is
 * arbitrary code, so it is gated on the CLI's {@code FULL} security mode (see
 * {@link HelmPluginExecGuard}). In {@code READ_ONLY} the files are installed and the hook
 * is skipped with a warning.
 */
@Slf4j
@Component
public class HelmPluginInstaller {

	private static final YAMLMapper YAML = YAMLMapper.builder().build();

	private final HelmPluginPaths paths;

	private final Supplier<HelmPluginEnvironment> environment;

	private final JhelmSecurityPolicy policy;

	private final HelmPluginRunner runner;

	private final GitCloner gitCloner;

	private final GitUpdater gitUpdater;

	/**
	 * Spring constructor.
	 * @param environmentFactory builds the {@code HELM_*} environment for hooks
	 * @param policy the security policy that gates hook execution
	 * @param runner runs install/update/delete hooks as a child process
	 */
	@Autowired
	public HelmPluginInstaller(HelmPluginEnvironmentFactory environmentFactory, JhelmSecurityPolicy policy,
			HelmPluginRunner runner) {
		this(HelmPluginPaths.fromEnvironment(), environmentFactory::create, policy, runner,
				HelmPluginInstaller::gitClone);
	}

	HelmPluginInstaller(HelmPluginPaths paths, Supplier<HelmPluginEnvironment> environment, JhelmSecurityPolicy policy,
			HelmPluginRunner runner, GitCloner gitCloner) {
		this(paths, environment, policy, runner, gitCloner, HelmPluginInstaller::gitPull);
	}

	HelmPluginInstaller(HelmPluginPaths paths, Supplier<HelmPluginEnvironment> environment, JhelmSecurityPolicy policy,
			HelmPluginRunner runner, GitCloner gitCloner, GitUpdater gitUpdater) {
		this.paths = paths;
		this.environment = environment;
		this.policy = policy;
		this.runner = runner;
		this.gitCloner = gitCloner;
		this.gitUpdater = gitUpdater;
	}

	/**
	 * Lists the Helm plugins installed under the Helm plugins directory.
	 * @return the installed Helm plugins, sorted by name
	 */
	public List<DiscoveredHelmPlugin> list() {
		return new HelmPluginDiscovery(this.paths).discover();
	}

	/**
	 * Uninstalls an installed Helm plugin: runs its {@code delete} hook, then removes its
	 * directory.
	 * @param name the plugin name
	 * @return {@code true} if a plugin was removed, {@code false} if none was installed
	 * under that name
	 * @throws IOException if the directory cannot be removed
	 * @throws InterruptedException if interrupted while running the delete hook
	 */
	public boolean uninstall(String name) throws IOException, InterruptedException {
		Optional<DiscoveredHelmPlugin> found = new HelmPluginDiscovery(this.paths).find(name);
		if (found.isEmpty()) {
			return false;
		}
		DiscoveredHelmPlugin plugin = found.get();
		String hook = (plugin.manifest().getHooks() != null) ? plugin.manifest().getHooks().getDelete() : null;
		try {
			runHook(plugin, hook, "delete");
		}
		catch (IOException ex) {
			// A failing delete hook must not strand the plugin files; log and remove
			// anyway.
			log.warn("delete hook for plugin '{}' failed, removing anyway: {}", name, ex.getMessage());
		}
		FileUtils.deleteDirectory(plugin.directory().toFile());
		return true;
	}

	/**
	 * Updates an installed Helm plugin: for a git-checked-out plugin, refreshes it with a
	 * {@code git pull}; then runs its {@code update} hook.
	 * @param name the plugin name
	 * @return the updated plugin, or empty if none is installed under that name
	 * @throws IOException if the update fails
	 * @throws InterruptedException if interrupted while updating or running the hook
	 */
	public Optional<DiscoveredHelmPlugin> update(String name) throws IOException, InterruptedException {
		HelmPluginDiscovery discovery = new HelmPluginDiscovery(this.paths);
		Optional<DiscoveredHelmPlugin> found = discovery.find(name);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		DiscoveredHelmPlugin plugin = found.get();
		if (Files.isDirectory(plugin.directory().resolve(".git"))) {
			this.gitUpdater.update(plugin.directory());
		}
		else {
			log.warn("plugin '{}' was not installed from git; running its update hook without refreshing sources",
					name);
		}
		DiscoveredHelmPlugin refreshed = discovery.find(name).orElse(plugin);
		String hook = (refreshed.manifest().getHooks() != null) ? refreshed.manifest().getHooks().getUpdate() : null;
		runHook(refreshed, hook, "update");
		return Optional.of(refreshed);
	}

	/**
	 * Installs a plugin from the given source.
	 * @param source a git URL, a {@code .tar.gz}/{@code .tgz} path or URL, or a local
	 * directory
	 * @param version an optional git ref (tag/branch/commit) for a git source, else
	 * {@code null}
	 * @return the installed plugin
	 * @throws IOException if the source cannot be fetched, is malformed, or the plugin is
	 * already installed
	 * @throws InterruptedException if interrupted while cloning or running the hook
	 */
	public DiscoveredHelmPlugin install(String source, String version) throws IOException, InterruptedException {
		Path staging = Files.createTempDirectory("jhelm-plugin-install");
		try {
			Path pluginRoot = stage(source, version, staging);
			HelmPluginManifest manifest = readManifest(pluginRoot);
			String name = resolveName(manifest, pluginRoot);
			manifest.setName(name);
			Path dest = this.paths.pluginsDir().resolve(name);
			if (Files.exists(dest)) {
				throw new IOException("plugin '" + name + "' is already installed (" + dest + ')');
			}
			Files.createDirectories(dest.getParent());
			FileUtils.copyDirectory(pluginRoot.toFile(), dest.toFile());
			DiscoveredHelmPlugin plugin = new DiscoveredHelmPlugin(name, dest, manifest);
			runInstallHook(plugin);
			return plugin;
		}
		finally {
			FileUtils.deleteQuietly(staging.toFile());
		}
	}

	private Path stage(String source, String version, Path staging) throws IOException, InterruptedException {
		String lower = source.toLowerCase(Locale.ROOT);
		if (isTarball(lower)) {
			Path archive = isUrl(lower) ? download(source, staging) : Path.of(source);
			extractTarGz(archive, staging);
			return findPluginRoot(staging);
		}
		if (isGit(lower)) {
			// Reject leading-dash values before cloning so a crafted source/ref cannot be
			// smuggled to git as a flag, regardless of the cloner implementation.
			if (source.startsWith("-")) {
				throw new IOException("invalid git url: " + source);
			}
			if (version != null && version.startsWith("-")) {
				throw new IOException("invalid git ref: " + version);
			}
			this.gitCloner.clone(source, version, staging);
			return findPluginRoot(staging);
		}
		Path dir = Path.of(source);
		if (Files.isDirectory(dir)) {
			FileUtils.copyDirectory(dir.toFile(), staging.toFile());
			return findPluginRoot(staging);
		}
		throw new IOException(
				"unrecognized plugin source (expected a git URL, a .tar.gz/.tgz, or a directory): " + source);
	}

	private static boolean isUrl(String lower) {
		return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("git://")
				|| lower.startsWith("ssh://");
	}

	private static boolean isTarball(String lower) {
		return lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
	}

	private static boolean isGit(String lower) {
		return lower.endsWith(".git") || lower.startsWith("git@") || lower.startsWith("git://")
				|| lower.startsWith("ssh://") || (isUrl(lower) && !isTarball(lower));
	}

	private static Path download(String url, Path staging) throws IOException, InterruptedException {
		Path target = staging.resolve("download.tgz");
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
		try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
			HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
			if (response.statusCode() / 100 != 2) {
				throw new IOException(
						"failed to download plugin archive: HTTP " + response.statusCode() + " for " + url);
			}
			return target;
		}
	}

	private static void extractTarGz(Path archive, Path dest) throws IOException {
		try (InputStream in = Files.newInputStream(archive);
				GZIPInputStream gzip = new GZIPInputStream(in);
				TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
			TarArchiveEntry entry;
			while ((entry = tar.getNextEntry()) != null) {
				Path resolved = dest.resolve(entry.getName()).normalize();
				if (!resolved.startsWith(dest)) {
					throw new IOException("archive entry escapes target directory: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(resolved);
				}
				else {
					Files.createDirectories(resolved.getParent());
					Files.copy(tar, resolved);
					// 0x40 is the owner-execute bit (S_IXUSR); preserve it so plugin
					// scripts stay runnable.
					if ((entry.getMode() & 0x40) != 0) {
						resolved.toFile().setExecutable(true);
					}
				}
			}
		}
	}

	/**
	 * Finds the directory containing {@code plugin.yaml}, descending through wrappers.
	 */
	private static Path findPluginRoot(Path base) throws IOException {
		if (hasManifest(base)) {
			return base;
		}
		try (Stream<Path> tree = Files.walk(base)) {
			return tree.filter(Files::isDirectory)
				.filter(HelmPluginInstaller::hasManifest)
				.findFirst()
				.orElseThrow(() -> new IOException("no plugin.yaml found in the plugin source"));
		}
	}

	private static boolean hasManifest(Path dir) {
		return Files.isRegularFile(dir.resolve("plugin.yaml")) || Files.isRegularFile(dir.resolve("plugin.yml"));
	}

	private static HelmPluginManifest readManifest(Path pluginRoot) throws IOException {
		Path manifest = Files.isRegularFile(pluginRoot.resolve("plugin.yaml")) ? pluginRoot.resolve("plugin.yaml")
				: pluginRoot.resolve("plugin.yml");
		return YAML.readValue(manifest.toFile(), HelmPluginManifest.class);
	}

	private static String resolveName(HelmPluginManifest manifest, Path pluginRoot) {
		if (manifest.getName() != null && !manifest.getName().isBlank()) {
			return manifest.getName();
		}
		return pluginRoot.getFileName().toString();
	}

	private void runInstallHook(DiscoveredHelmPlugin plugin) throws IOException, InterruptedException {
		String hook = (plugin.manifest().getHooks() != null) ? plugin.manifest().getHooks().getInstall() : null;
		runHook(plugin, hook, "install");
	}

	private void runHook(DiscoveredHelmPlugin plugin, String hook, String phase)
			throws IOException, InterruptedException {
		if (hook == null || hook.isBlank()) {
			return;
		}
		if (HelmPluginExecGuard.blocked(this.policy)) {
			log.warn("{} hook for plugin '{}' skipped in READ_ONLY mode — the plugin may be incomplete", phase,
					plugin.name());
			return;
		}
		Map<String, String> env = this.environment.get().forPlugin(plugin);
		String expanded = DiscoveredHelmPlugin.expand(hook, env);
		int code = this.runner.run(List.of("sh", "-c", expanded), env, plugin.directory());
		if (code != 0) {
			throw new IOException(phase + " hook for plugin '" + plugin.name() + "' failed (exit " + code + ')');
		}
	}

	private static void gitPull(Path dir) throws IOException, InterruptedException {
		List<String> command = List.of("git", "-C", dir.toString(), "pull", "--ff-only");
		ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
		builder.environment().put("GIT_TERMINAL_PROMPT", "0");
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes());
		int code = process.waitFor();
		if (code != 0) {
			throw new IOException("git pull failed (exit " + code + "): " + output.strip());
		}
	}

	private static void gitClone(String url, String ref, Path dest) throws IOException, InterruptedException {
		// Reject leading-dash values so a crafted url/ref cannot smuggle git flags, and
		// end
		// option parsing with "--" before the positional url/dest (defense in depth).
		if (url.startsWith("-")) {
			throw new IOException("invalid git url: " + url);
		}
		if (ref != null && ref.startsWith("-")) {
			throw new IOException("invalid git ref: " + ref);
		}
		List<String> command = new ArrayList<>(List.of("git", "clone", "--depth", "1"));
		if (ref != null && !ref.isBlank()) {
			command.add("--branch");
			command.add(ref);
		}
		command.add("--");
		command.add(url);
		command.add(dest.toString());
		ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
		builder.environment().put("GIT_TERMINAL_PROMPT", "0");
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes());
		int code = process.waitFor();
		if (code != 0) {
			throw new IOException("git clone failed (exit " + code + "): " + output.strip());
		}
	}

}
