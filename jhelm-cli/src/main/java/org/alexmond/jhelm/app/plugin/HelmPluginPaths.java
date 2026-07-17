package org.alexmond.jhelm.app.plugin;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * Resolves the Helm home directories the way the {@code helm} CLI does, so jhelm
 * discovers plugins from the same locations. Resolution order for each directory is: the
 * dedicated {@code HELM_*} environment variable, then the matching XDG base dir with a
 * {@code helm} suffix, then the platform default under the user's home.
 *
 * <p>
 * The environment lookup and home directory are injected so the resolver is unit-testable
 * without touching the real process environment; {@link #fromEnvironment()} wires the
 * production sources ({@link System#getenv(String)} and {@code user.home}).
 *
 * @see <a href="https://helm.sh/docs/helm/helm/">helm environment variables</a>
 */
public final class HelmPluginPaths {

	private final UnaryOperator<String> env;

	private final Path home;

	HelmPluginPaths(UnaryOperator<String> env, Path home) {
		this.env = env;
		this.home = home;
	}

	/**
	 * Builds a resolver backed by the real process environment and {@code user.home}.
	 * @return an environment-backed resolver
	 */
	public static HelmPluginPaths fromEnvironment() {
		return new HelmPluginPaths(System::getenv, Path.of(System.getProperty("user.home", ".")));
	}

	/**
	 * The Helm data home ({@code $HELM_DATA_HOME}, else {@code $XDG_DATA_HOME/helm}, else
	 * {@code ~/.local/share/helm}).
	 * @return the data home directory
	 */
	public Path dataHome() {
		return resolve("HELM_DATA_HOME", "XDG_DATA_HOME", ".local", "share");
	}

	/**
	 * The Helm config home ({@code $HELM_CONFIG_HOME}, else
	 * {@code $XDG_CONFIG_HOME/helm}, else {@code ~/.config/helm}).
	 * @return the config home directory
	 */
	public Path configHome() {
		return resolve("HELM_CONFIG_HOME", "XDG_CONFIG_HOME", ".config");
	}

	/**
	 * The Helm cache home ({@code $HELM_CACHE_HOME}, else {@code $XDG_CACHE_HOME/helm},
	 * else {@code ~/.cache/helm}).
	 * @return the cache home directory
	 */
	public Path cacheHome() {
		return resolve("HELM_CACHE_HOME", "XDG_CACHE_HOME", ".cache");
	}

	/**
	 * The plugin directory Helm installs into and discovers from: {@code $HELM_PLUGINS}
	 * if set, otherwise {@code <dataHome>/plugins}.
	 * @return the plugins directory
	 */
	public Path pluginsDir() {
		String override = value("HELM_PLUGINS");
		return (override != null) ? Path.of(override) : dataHome().resolve("plugins");
	}

	private Path resolve(String helmVar, String xdgVar, String... homeSegments) {
		String helm = value(helmVar);
		if (helm != null) {
			return Path.of(helm);
		}
		String xdg = value(xdgVar);
		if (xdg != null) {
			return Path.of(xdg).resolve("helm");
		}
		Path base = this.home;
		for (String segment : homeSegments) {
			base = base.resolve(segment);
		}
		return base.resolve("helm");
	}

	private String value(String name) {
		String raw = this.env.apply(name);
		return (raw == null || raw.isBlank()) ? null : raw;
	}

}
