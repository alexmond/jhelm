package org.alexmond.jhelm.core.config;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for jhelm test infrastructure, bound under the
 * {@code jhelmtest} prefix.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelmtest")
public class JhelmTestProperties {

	/**
	 * Number of top charts to fetch from Artifact Hub for comparison tests. Defaults to
	 * 30.
	 */
	private int numberOfTopCharts = 5;

	/**
	 * Ignore rules for manifest comparison, keyed by chart name ({@code "*"} for global
	 * rules).
	 */
	private Map<String, List<IgnoreRule>> comparisonIgnores = Map.of();

	/**
	 * Value overrides applied to both Helm and jhelm rendering, keyed by chart name.
	 * Supplies the mandatory values some charts require so they can be compared (e.g. a
	 * cluster name or hostname). The same overrides go to {@code helm template -f} and to
	 * the jhelm install, so any divergence is a real jhelm bug.
	 */
	private Map<String, Map<String, Object>> comparisonValues = Map.of();

	/**
	 * A single comparison-ignore rule describing a resource/path pattern to skip during
	 * manifest diff.
	 */
	@Getter
	@Setter
	public static class IgnoreRule {

		private String resource = "*";

		private String path = "*";

		private String reason = "";

	}

}
