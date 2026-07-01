/**
 * Internal implementation details of the jhelm Kubernetes module — <strong>not part of
 * the supported public API</strong>. Types in this package (and any {@code .internal}
 * package elsewhere in jhelm) may change or be removed in any release without notice.
 *
 * <p>
 * They are {@code public} only because the module's Spring auto-configuration, which
 * lives in a sibling package, must construct them. Depend on the published interfaces
 * (e.g. {@link org.alexmond.jhelm.core.service.KubeService}) and the auto-configured
 * beans instead.
 */
package org.alexmond.jhelm.kube.service.internal;
