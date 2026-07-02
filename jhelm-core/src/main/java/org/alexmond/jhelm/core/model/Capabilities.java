package org.alexmond.jhelm.core.model;

import java.util.List;

/**
 * Overrides for the Helm {@code .Capabilities} built-in used during template rendering.
 *
 * <p>
 * A {@code null} {@link #kubeVersion()} means "use the engine's built-in default
 * Kubernetes version"; a non-null value (e.g. {@code "v1.29.0"}) sets
 * {@code .Capabilities.KubeVersion} so charts that gate on the cluster version render as
 * if against that server. {@link #extraApiVersions()} are advertised <em>in addition
 * to</em> the engine's built-in default API-version set, so
 * {@code .Capabilities.APIVersions.Has "custom.io/v1"} can be made to succeed for
 * group/versions the defaults don't include.
 *
 * <p>
 * Populated two ways: from a live cluster by {@code KubeService.getCapabilities()} during
 * install/upgrade, or from the {@code --kube-version} / {@code --api-versions} flags of
 * the offline {@code template} command. {@link #DEFAULT} leaves everything at the engine
 * defaults.
 *
 * @param kubeVersion the Kubernetes version string, or {@code null} for the engine
 * default
 * @param extraApiVersions additional API group/versions to advertise (never {@code null})
 */
public record Capabilities(String kubeVersion, List<String> extraApiVersions) {

	/** Use the engine's built-in defaults for both the kube version and API versions. */
	public static final Capabilities DEFAULT = new Capabilities(null, List.of());

	public Capabilities {
		extraApiVersions = (extraApiVersions != null) ? List.copyOf(extraApiVersions) : List.of();
	}

}
