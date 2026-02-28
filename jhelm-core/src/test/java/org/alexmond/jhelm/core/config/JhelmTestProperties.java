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
