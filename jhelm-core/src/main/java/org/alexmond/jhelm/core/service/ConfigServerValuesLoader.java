package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.config.ConfigServerProperties;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.model.Environment;
import org.alexmond.jhelm.core.util.PropertySourceMapper;

/**
 * Resolves the effective config-server request (merging {@link ConfigServerProperties}
 * with per-command {@code --config-*} overrides), fetches the Environment, and reduces it
 * to a nested values map. Honors {@code fail-fast} (abort vs warn-and-continue) and
 * surfaces the precedence flags ({@code override-none} /
 * {@code override-system-properties}) so the caller can place the values correctly in the
 * override chain.
 * <p>
 * A reusable component: the CLI drives it today; library/REST/MCP callers can too.
 * Disabled by default — when neither the property nor a {@code --config-server-uri}
 * enables it, it returns an empty result and makes no network call.
 */
@Slf4j
public class ConfigServerValuesLoader {

	private final ConfigServerProperties properties;

	private final ConfigServerClient client;

	public ConfigServerValuesLoader(ConfigServerProperties properties, ConfigServerClient client) {
		this.properties = properties;
		this.client = client;
	}

	/**
	 * Fetch config-server values for a release.
	 * @param releaseName the release name, used as the default config-server application
	 * name
	 * @param activeProfiles the active profiles (from {@code --profile}/#670)
	 * @param overrides per-command {@code --config-*} overrides (may be empty)
	 * @return the fetched values plus precedence flags; empty values when disabled or on
	 * a non-fatal fetch failure
	 * @throws JhelmException when the fetch fails and {@code fail-fast} is set
	 */
	public Result load(String releaseName, List<String> activeProfiles, Options overrides) {
		Options cli = (overrides != null) ? overrides : Options.none();
		String uri = firstNonBlank(cli.uri(), properties.getUri());
		boolean enabled = properties.isEnabled() || (cli.uri() != null && !cli.uri().isBlank());
		if (!enabled || uri == null || uri.isBlank()) {
			return new Result(Map.of(), properties.isOverrideNone(), properties.isOverrideSystemProperties());
		}

		String application = firstNonBlank(cli.name(), properties.getName(), releaseName);
		ConfigServerRequest request = new ConfigServerRequest(uri, application, activeProfiles,
				firstNonBlank(cli.label(), properties.getLabel()),
				firstNonBlank(cli.username(), properties.getUsername()),
				firstNonBlank(cli.password(), properties.getPassword()),
				firstNonBlank(cli.token(), properties.getToken()));
		boolean failFast = (cli.failFast() != null) ? cli.failFast() : properties.isFailFast();

		Map<String, Object> values;
		try {
			Environment environment = client.fetch(request);
			values = PropertySourceMapper.toValues(environment);
		}
		catch (Exception ex) {
			if (failFast) {
				throw new JhelmException("Config server fetch failed: " + ex.getMessage(), ex);
			}
			if (log.isWarnEnabled()) {
				log.warn("Config server fetch failed, continuing with local values only: {}", ex.getMessage());
			}
			values = Map.of();
		}
		return new Result(values, properties.isOverrideNone(), properties.isOverrideSystemProperties());
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	/**
	 * Per-command {@code --config-*} overrides. A {@code null} field defers to the
	 * corresponding {@link ConfigServerProperties} value.
	 *
	 * @param uri overrides {@code jhelm.config-server.uri}
	 * @param name overrides {@code jhelm.config-server.name}
	 * @param label overrides {@code jhelm.config-server.label}
	 * @param username overrides {@code jhelm.config-server.username}
	 * @param password overrides {@code jhelm.config-server.password}
	 * @param token overrides {@code jhelm.config-server.token}
	 * @param failFast overrides {@code jhelm.config-server.fail-fast}
	 */
	public record Options(String uri, String name, String label, String username, String password, String token,
			Boolean failFast) {

		/**
		 * @return an all-{@code null} override set (defer entirely to properties).
		 */
		public static Options none() {
			return new Options(null, null, null, null, null, null, null);
		}

	}

	/**
	 * The resolved config-server values and where they sit in the override chain.
	 *
	 * @param values the fetched, un-flattened values (empty when disabled or non-fatal
	 * failure)
	 * @param overrideNone config-server drops to lowest precedence
	 * @param overrideSystemProperties config-server outranks the {@code --set} family
	 */
	public record Result(Map<String, Object> values, boolean overrideNone, boolean overrideSystemProperties) {
	}

}
