package org.alexmond.jhelm.core.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The contents of a chart's {@code Chart.yaml}: name, version, description, API version,
 * type, app version, supported Kubernetes range, dependency declarations and annotations.
 * Unknown keys are ignored on deserialization.
 *
 * <p>
 * <strong>Mutability contract (1.0):</strong> this is a mutable internal model. The
 * loader and engine mutate it during loading and rendering — the loader backfills
 * v1-chart {@code requirements.yaml} dependencies, and the engine sets the
 * {@code .Chart.IsRoot} flag (and, for aliased dependencies, the effective name) as it
 * walks the render tree. Treat an instance obtained from the API as read-only in your own
 * code and construct new ones via the generated {@code builder()}; the {@code set*}
 * methods exist for the loading/rendering pipeline, not as a supported mutation surface.
 * (Contrast {@link Release}, which is fully immutable.)
 */
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

	/**
	 * Exposes Helm's {@code .Chart.IsRoot} flag to templates: {@code true} when this is
	 * the top-level release chart, {@code false} when rendered as a subchart.
	 * @return {@code true} if this chart is the root of the release
	 */
	@JsonIgnore
	@SuppressWarnings("PMD.BooleanGetMethodName")
	public boolean getIsRoot() {
		return Boolean.TRUE.equals(this.root);
	}

}
