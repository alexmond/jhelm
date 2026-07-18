package org.alexmond.jhelm.core.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads jhelm Java plugins from external JAR files that are not on the application
 * classpath. Each configured directory is scanned for {@code *.jar}; every jar gets its
 * own {@link URLClassLoader} whose parent is jhelm-core's class loader, so the plugin
 * resolves the {@code jhelm-plugin-api} types while its declared
 * {@link java.util.ServiceLoader} services are discovered per plugin interface. Isolating
 * each jar in its own loader tolerates conflicting transitive dependency versions between
 * plugins.
 *
 * <p>
 * This complements {@link org.alexmond.jhelm.pluginapi.JhelmPlugins}, which discovers
 * plugins already on the classpath (as Spring beans or classpath {@code ServiceLoader}
 * services); the plugins returned here are supplied to
 * {@link org.alexmond.jhelm.pluginapi.JhelmPlugins#merge(Class, java.util.Collection)}
 * alongside them.
 */
// This class deliberately manages plugin class loaders: it parents them on jhelm-core's
// own loader (UseProperClassLoader), holds them open for the application lifetime and
// closes them in close() (CloseResource), and compares defining-loader identity to keep
// only jar-defined plugins (CompareObjectsWithEquals).
@SuppressWarnings({ "PMD.UseProperClassLoader", "PMD.CloseResource", "PMD.CompareObjectsWithEquals" })
@Slf4j
public class PluginLoader implements AutoCloseable {

	private final List<URLClassLoader> loaders;

	/**
	 * Scans the given directories for plugin jars and opens a class loader for each.
	 * @param directories directories to scan for {@code *.jar} files (may be {@code null}
	 * or empty, in which case nothing is loaded)
	 */
	public PluginLoader(List<String> directories) {
		this.loaders = buildLoaders(directories);
	}

	private static List<URLClassLoader> buildLoaders(List<String> directories) {
		if (directories == null || directories.isEmpty()) {
			return List.of();
		}
		List<URLClassLoader> built = new ArrayList<>();
		ClassLoader parent = PluginLoader.class.getClassLoader();
		for (String dir : directories) {
			if (dir == null || dir.isBlank()) {
				continue;
			}
			Path root = Path.of(dir);
			if (!Files.isDirectory(root)) {
				log.warn("jhelm plugin directory does not exist or is not a directory: {}", root);
				continue;
			}
			collectJars(root, parent, built);
		}
		return built;
	}

	private static void collectJars(Path root, ClassLoader parent, List<URLClassLoader> built) {
		try (Stream<Path> entries = Files.list(root)) {
			List<Path> jars = entries
				.filter((p) -> Files.isRegularFile(p)
						&& p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
				.sorted()
				.toList();
			for (Path jar : jars) {
				try {
					URL url = jar.toUri().toURL();
					built.add(new URLClassLoader(new URL[] { url }, parent));
					log.info("Loaded jhelm plugin jar: {}", jar);
				}
				catch (MalformedURLException ex) {
					log.warn("Skipping unreadable plugin jar {}: {}", jar, ex.getMessage());
				}
			}
		}
		catch (IOException ex) {
			log.warn("Failed to scan jhelm plugin directory {}: {}", root, ex.getMessage());
		}
	}

	/**
	 * Discovers external-jar plugins of the given interface across every loaded jar. Only
	 * plugin classes actually defined by a plugin jar are returned; services that resolve
	 * to the shared parent classpath are left for the caller's classpath discovery to
	 * avoid double-counting.
	 * @param service the plugin interface to load
	 * @param <T> the plugin type
	 * @return the discovered plugin instances (empty if no external jars were loaded)
	 */
	public <T> List<T> load(Class<T> service) {
		List<T> found = new ArrayList<>();
		for (URLClassLoader loader : loaders) {
			for (T plugin : ServiceLoader.load(service, loader)) {
				if (plugin.getClass().getClassLoader() == loader) {
					found.add(plugin);
				}
			}
		}
		return found;
	}

	/**
	 * Returns the number of external plugin jars that were loaded.
	 * @return the loaded jar count
	 */
	public int jarCount() {
		return this.loaders.size();
	}

	@Override
	public void close() {
		for (URLClassLoader loader : this.loaders) {
			try {
				loader.close();
			}
			catch (IOException ex) {
				log.debug("Error closing plugin class loader: {}", ex.getMessage());
			}
		}
	}

}
