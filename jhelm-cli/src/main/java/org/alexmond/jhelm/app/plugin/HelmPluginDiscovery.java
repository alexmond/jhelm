package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Discovers Helm plugins installed under the Helm plugins directory. Each immediate
 * subdirectory that contains a {@code plugin.yaml} (or {@code plugin.yml}) is parsed into
 * a {@link DiscoveredHelmPlugin}; directories without a manifest, or with an unparseable
 * one, are skipped with a warning so a single bad plugin does not break discovery.
 *
 * @see HelmPluginPaths#pluginsDir()
 */
@Slf4j
public class HelmPluginDiscovery {

	private static final YAMLMapper YAML = YAMLMapper.builder().build();

	private final HelmPluginPaths paths;

	/**
	 * Creates a discovery over the given path resolver.
	 * @param paths the Helm path resolver whose {@link HelmPluginPaths#pluginsDir()} is
	 * scanned
	 */
	public HelmPluginDiscovery(HelmPluginPaths paths) {
		this.paths = paths;
	}

	/**
	 * Scans the plugins directory and returns every parseable Helm plugin, sorted by
	 * name.
	 * @return the discovered plugins (empty if the directory is absent or empty)
	 */
	public List<DiscoveredHelmPlugin> discover() {
		Path dir = this.paths.pluginsDir();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		List<DiscoveredHelmPlugin> found = new ArrayList<>();
		try (Stream<Path> entries = Files.list(dir)) {
			entries.filter(Files::isDirectory).sorted().forEach((pluginDir) -> load(pluginDir).ifPresent(found::add));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("failed to list Helm plugins directory " + dir, ex);
		}
		return found;
	}

	/**
	 * Finds a single installed plugin by name.
	 * @param name the plugin name to look up
	 * @return the matching plugin, or empty if none is installed under that name
	 */
	public Optional<DiscoveredHelmPlugin> find(String name) {
		return discover().stream().filter((plugin) -> plugin.name().equals(name)).findFirst();
	}

	private Optional<DiscoveredHelmPlugin> load(Path pluginDir) {
		Path manifestFile = manifestFile(pluginDir);
		if (manifestFile == null) {
			return Optional.empty();
		}
		try {
			HelmPluginManifest manifest = YAML.readValue(manifestFile.toFile(), HelmPluginManifest.class);
			String name = (manifest.getName() != null && !manifest.getName().isBlank()) ? manifest.getName()
					: pluginDir.getFileName().toString();
			manifest.setName(name);
			return Optional.of(new DiscoveredHelmPlugin(name, pluginDir, manifest));
		}
		catch (RuntimeException ex) {
			log.warn("skipping unparseable Helm plugin at {}: {}", pluginDir, ex.getMessage());
			return Optional.empty();
		}
	}

	private static Path manifestFile(Path pluginDir) {
		Path yaml = pluginDir.resolve("plugin.yaml");
		if (Files.isRegularFile(yaml)) {
			return yaml;
		}
		Path yml = pluginDir.resolve("plugin.yml");
		return Files.isRegularFile(yml) ? yml : null;
	}

}
