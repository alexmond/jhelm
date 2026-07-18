package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves a {@code --post-renderer} value into the command to run. Mirrors Helm 3.10+:
 * the value is used as a filesystem path when it points at an existing file, otherwise it
 * is looked up as an installed Helm plugin by name and resolved to that plugin's command
 * (expanding {@code $HELM_PLUGIN_DIR}). Any {@code --post-renderer-args} are appended.
 *
 * <p>
 * Resolving to a plugin (arbitrary local code) is gated on the CLI's {@code FULL}
 * security mode; a plain path keeps jhelm's existing behavior.
 */
@Component
public class HelmPostRendererResolver {

	private final HelmPluginPaths paths;

	private final Supplier<HelmPluginEnvironment> environment;

	private final JhelmSecurityPolicy policy;

	/**
	 * Spring constructor.
	 * @param environmentFactory builds the {@code HELM_*} environment used to expand the
	 * plugin command
	 * @param policy the security policy that gates resolving to a native plugin
	 */
	@Autowired
	public HelmPostRendererResolver(HelmPluginEnvironmentFactory environmentFactory, JhelmSecurityPolicy policy) {
		this(HelmPluginPaths.fromEnvironment(), environmentFactory::create, policy);
	}

	HelmPostRendererResolver(HelmPluginPaths paths, Supplier<HelmPluginEnvironment> environment,
			JhelmSecurityPolicy policy) {
		this.paths = paths;
		this.environment = environment;
		this.policy = policy;
	}

	/**
	 * Creates a resolver with no plugins directory, so every post-renderer reference
	 * resolves as a plain file path (no plugin lookup). Intended for callers and tests
	 * that do not use plugin post-renderers.
	 * @param policy the security policy
	 * @return a file-path-only resolver
	 */
	public static HelmPostRendererResolver fileOnly(JhelmSecurityPolicy policy) {
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", "/nonexistent-jhelm-plugins")::get,
				Path.of("/nonexistent-jhelm-plugins"));
		return new HelmPostRendererResolver(paths, () -> HelmPluginEnvironment.builder().paths(paths).build(), policy);
	}

	/**
	 * Resolves a post-renderer reference to a concrete command.
	 * @param value a path to an executable, or the name of an installed Helm plugin
	 * @param args extra arguments to append (from {@code --post-renderer-args}), may be
	 * {@code null}
	 * @return the command vector to run as a post-renderer
	 * @throws IOException if the value names a plugin but plugin execution is disallowed,
	 * or the plugin declares no command for the current platform
	 */
	public List<String> resolve(String value, List<String> args) throws IOException {
		List<String> extra = (args != null) ? args : List.of();
		if (Files.isRegularFile(Path.of(value))) {
			return append(List.of(value), extra);
		}
		Optional<DiscoveredHelmPlugin> plugin = new HelmPluginDiscovery(this.paths).find(value);
		if (plugin.isPresent()) {
			if (this.policy.mode() != JhelmAccessMode.FULL) {
				throw new IOException("running native Helm plugins is disabled in READ_ONLY mode — set "
						+ "jhelm.security.mode=FULL to enable");
			}
			Map<String, String> env = this.environment.get().forPlugin(plugin.get());
			List<String> command = plugin.get()
				.resolveCommand(HelmPlatform.currentOs(), HelmPlatform.currentArch(), env);
			if (command.isEmpty()) {
				throw new IOException("post-renderer plugin '" + value + "' declares no command for this platform");
			}
			return append(command, extra);
		}
		// Not a file and not an installed plugin: pass through verbatim (fails at exec,
		// as
		// Helm does when a post-renderer cannot be found).
		return append(List.of(value), extra);
	}

	private static List<String> append(List<String> command, List<String> extra) {
		List<String> full = new ArrayList<>(command);
		full.addAll(extra);
		return full;
	}

}
