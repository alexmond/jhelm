package org.alexmond.jhelm.gotemplate.helm.kubernetes;

import java.util.Map;

/**
 * Provider interface for Kubernetes operations.
 * This allows jhelm-gotemplate to remain lightweight and not depend on Kubernetes client libraries.
 * Implementations can be provided by jhelm-kube or other modules.
 */
public interface KubernetesProvider {

    /**
     * Lookup a Kubernetes resource by API version, kind, namespace, and name.
     *
     * @param apiVersion The API version (e.g., "v1", "apps/v1")
     * @param kind       The resource kind (e.g., "Pod", "Service", "Deployment")
     * @param namespace  The namespace (empty string for cluster-scoped resources)
     * @param name       The resource name (empty string to list all)
     * @return Map representation of the resource, or empty map if not found
     */
    Map<String, Object> lookup(String apiVersion, String kind, String namespace, String name);

    /**
     * Get Kubernetes cluster version information.
     *
     * @return Map with version info (Major, Minor, GitVersion, etc.)
     */
    Map<String, Object> getVersion();

    /**
     * Check if this provider is available and can make Kubernetes calls.
     *
     * @return true if Kubernetes API is accessible
     */
    boolean isAvailable();
}
