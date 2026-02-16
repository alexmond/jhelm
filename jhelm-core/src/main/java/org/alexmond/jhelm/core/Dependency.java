package org.alexmond.jhelm.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a chart dependency as defined in Chart.yaml.
 * <p>
 * Example in Chart.yaml:
 * <pre>
 * dependencies:
 *   - name: postgresql
 *     version: "^12.1.0"
 *     repository: "https://charts.bitnami.com/bitnami"
 *     condition: postgresql.enabled
 *     tags:
 *       - database
 *     alias: postgres
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dependency {
    /**
     * Name of the chart dependency.
     */
    private String name;

    /**
     * Version constraint for the dependency (e.g., "^1.2.3", "~1.2.0", ">=1.0.0 <2.0.0").
     * Supports semver range expressions.
     */
    private String version;

    /**
     * Repository URL or alias where the dependency can be found.
     * Can be:
     * <ul>
     *   <li>A full URL (e.g., "https://charts.bitnami.com/bitnami")</li>
     *   <li>An OCI registry URL (e.g., "oci://registry.example.com/charts")</li>
     *   <li>A repository alias (e.g., "@stable")</li>
     *   <li>A local file path (e.g., "file://../other-chart")</li>
     * </ul>
     */
    private String repository;

    /**
     * Optional condition that must evaluate to {@code true} for this dependency to be included.
     * The condition references a value in values.yaml (e.g., "postgresql.enabled").
     */
    private String condition;

    /**
     * Optional list of tags. The dependency is included if any of these tags are enabled.
     */
    private List<String> tags;

    /**
     * Optional alias name to use for this dependency instead of the original name.
     */
    private String alias;

    /**
     * Optional list of values to import from the dependency chart.
     * Can be a list of strings or maps for more complex import configurations.
     */
    @JsonProperty("import-values")
    private List<Object> importValues;
}
