package org.alexmond.jhelm.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-scans the raw command line for Helm's global persistent flags and maps them to
 * jhelm Spring properties.
 * <p>
 * These flags ({@code --kubeconfig}, {@code --kube-context}, {@code --kube-apiserver},
 * {@code --registry-config}, {@code --repository-config}, {@code --repository-cache},
 * {@code --debug}) configure beans that are built eagerly at startup — the Kubernetes
 * client, the repository manager, the registry manager — so they must be resolved
 * <em>before</em> the Spring context is created, not during Picocli execution. This
 * parser runs in {@code main()}: the mapped values are set as JVM system properties
 * (authoritative over environment and defaults), and the flags are stripped from the
 * argument vector handed to Spring so the bare {@code --debug} does not trigger Spring
 * Boot's own autoconfiguration report. Picocli still receives the original arguments (the
 * flags are declared as inherited options on the root command), so they appear in
 * {@code --help} and are accepted anywhere.
 */
public final class GlobalOptionsPreParser {

	/** Select the kube context from the kubeconfig. */
	public static final String KUBE_CONTEXT = "--kube-context";

	/** Path to the kubeconfig file. */
	public static final String KUBECONFIG = "--kubeconfig";

	/** Override the Kubernetes API server address. */
	public static final String KUBE_APISERVER = "--kube-apiserver";

	/** Path to the OCI registry config ({@code config.json}). */
	public static final String REGISTRY_CONFIG = "--registry-config";

	/** Path to the repository config ({@code repositories.yaml}). */
	public static final String REPOSITORY_CONFIG = "--repository-config";

	/** Path to the repository cache directory. */
	public static final String REPOSITORY_CACHE = "--repository-cache";

	/** Enable verbose (debug) logging. */
	public static final String DEBUG = "--debug";

	/**
	 * Directory (or comma-separated directories) scanned for external jhelm Java plugin
	 * JARs. jhelm-specific — Helm has no equivalent flag.
	 */
	public static final String PLUGIN_DIR = "--plugin-dir";

	// Value-taking global flags mapped to their jhelm Spring property.
	private static final Map<String, String> VALUE_FLAGS = Map.of(KUBE_CONTEXT, "jhelm.kubernetes.context", KUBECONFIG,
			"jhelm.kubernetes.kubeconfig-path", KUBE_APISERVER, "jhelm.kubernetes.api-server", REGISTRY_CONFIG,
			"jhelm.registry-config-path", REPOSITORY_CONFIG, "jhelm.config-path", REPOSITORY_CACHE,
			"jhelm.repository-cache-path", PLUGIN_DIR, "jhelm.plugins.path");

	private GlobalOptionsPreParser() {
	}

	/**
	 * Scans the raw arguments for the global flags.
	 * @param args the raw command-line arguments
	 * @return the derived system properties and the Spring argument vector
	 */
	public static Result parse(String[] args) {
		Map<String, String> props = new LinkedHashMap<>();
		List<String> springArgs = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			String name = arg;
			String inlineValue = null;
			int eq = arg.indexOf('=');
			if (arg.startsWith("--") && eq > 0) {
				name = arg.substring(0, eq);
				inlineValue = arg.substring(eq + 1);
			}
			if (DEBUG.equals(name)) {
				props.put("logging.level.org.alexmond.jhelm", "DEBUG");
				continue;
			}
			String property = VALUE_FLAGS.get(name);
			if (property != null) {
				String value = inlineValue;
				if (value == null && i + 1 < args.length) {
					value = args[++i];
				}
				if (value != null && !value.isBlank()) {
					props.put(property, value);
				}
				continue;
			}
			springArgs.add(arg);
		}
		return new Result(props, springArgs.toArray(new String[0]));
	}

	/**
	 * The result of pre-parsing: the system properties to set before the Spring context
	 * is built, and the argument vector to hand to Spring (with the global flags
	 * removed).
	 *
	 * @param systemProperties jhelm Spring properties derived from the global flags
	 * @param springArgs the original arguments with the global flags (and their values)
	 * stripped
	 */
	public record Result(Map<String, String> systemProperties, String[] springArgs) {
	}

}
