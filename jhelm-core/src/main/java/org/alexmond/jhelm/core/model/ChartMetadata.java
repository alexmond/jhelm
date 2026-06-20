package org.alexmond.jhelm.core.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartMetadata {

	private String name;

	private String version;

	private String description;

	private String apiVersion; // e.g., "v2"

	private String type; // e.g., "application"

	private String appVersion;

	// The chart's supported Kubernetes version range from Chart.yaml (Helm's
	// .Chart.KubeVersion), e.g. ">=1.25.0-0". Charts interpolate it directly, e.g.
	// kyverno-policies' `default .Chart.KubeVersion .Values.kubeVersionOverride`.
	private String kubeVersion;

	private List<Dependency> dependencies;

	private Map<String, String> annotations;

}
