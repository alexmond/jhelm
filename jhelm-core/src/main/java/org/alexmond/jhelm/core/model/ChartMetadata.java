package org.alexmond.jhelm.core.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

	// Helm's .Chart.IsRoot: true for the chart being installed (the top-level release
	// chart), false when rendered as a subchart. Set per render by the engine, so it is
	// excluded from Chart.yaml (de)serialization. Charts branch on it, e.g.
	// signoz/k8s-infra's otel.endpoint only builds a default `{{ if not .Chart.IsRoot
	// }}`.
	// The explicit getIsRoot() exposes it to templates as `.Chart.IsRoot` (Lombok's
	// boolean getter would be isRoot()/property "root", resolving as .Chart.Root
	// instead).
	// Boxed Boolean (not primitive) so Lombok's @AllArgsConstructor — which Jackson uses
	// as the creator — accepts a null when the key is absent from Chart.yaml instead of
	// failing FAIL_ON_NULL_FOR_PRIMITIVES.
	@JsonIgnore
	private Boolean root;

	@JsonIgnore
	@SuppressWarnings("PMD.BooleanGetMethodName")
	public boolean getIsRoot() {
		return Boolean.TRUE.equals(this.root);
	}

}
