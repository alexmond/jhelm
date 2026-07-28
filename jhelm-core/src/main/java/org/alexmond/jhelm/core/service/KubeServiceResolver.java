package org.alexmond.jhelm.core.service;

/**
 * Per-request cluster-selection seam for hosts that embed jhelm against more than one
 * Kubernetes cluster.
 * <p>
 * By default jhelm is single-cluster: every action bean is wired to the one singleton
 * {@link KubeService}. A multi-cluster host (one that addresses clusters by id) can
 * instead supply a {@code KubeServiceResolver} bean — typically request-scoped — that
 * inspects the current request (a header, a path variable, a thread-local, an id on a
 * {@code SecurityContext}, etc.) and returns the {@link KubeService} for the target
 * cluster.
 * <p>
 * When a {@code KubeServiceResolver} bean is present, jhelm-core exposes a
 * {@link org.springframework.context.annotation.Primary @Primary} delegating
 * {@link KubeService} that routes every call through {@link #resolve()}, so the release
 * actions transparently operate on the per-request cluster. With no resolver bean the
 * delegating service is absent and the actions inject the real singleton
 * {@link KubeService} exactly as before — the default single-cluster behavior is
 * unchanged.
 * <p>
 * Implementations must return a non-{@code null} {@link KubeService}; {@link #resolve()}
 * is invoked once per {@link KubeService} method call, so an implementation is free to
 * return a different service on each invocation.
 */
@FunctionalInterface
public interface KubeServiceResolver {

	/**
	 * Returns the {@link KubeService} for the cluster selected by the current request.
	 * Invoked once per delegated {@link KubeService} method call.
	 * @return the target cluster's {@link KubeService}, never {@code null}
	 */
	KubeService resolve();

}
