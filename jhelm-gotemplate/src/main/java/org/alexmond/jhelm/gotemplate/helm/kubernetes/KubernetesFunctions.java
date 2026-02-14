package org.alexmond.jhelm.gotemplate.helm.kubernetes;

import org.alexmond.jhelm.gotemplate.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * Helm Kubernetes-specific functions
 * Based on: <a href="https://helm.sh/docs/chart_template_guide/function_list/">https://helm.sh/docs/chart_template_guide/function_list/</a>
 * <p>
 * These functions provide access to Kubernetes cluster resources during template rendering.
 * Requires a KubernetesProvider implementation to be provided via getFunctions(provider).
 */
public class KubernetesFunctions {

    /**
     * Get Kubernetes functions with a provider implementation.
     * Use this when Kubernetes API access is available.
     *
     * @param provider KubernetesProvider implementation for actual Kubernetes access
     * @return Map of function name to Function implementation
     */
    public static Map<String, Function> getFunctions(KubernetesProvider provider) {
        Map<String, Function> functions = new HashMap<>();

        functions.put("lookup", lookup(provider));
        functions.put("kubeVersion", kubeVersion(provider));

        return functions;
    }

    /**
     * Get Kubernetes functions without a provider (returns stub implementations).
     * Use this for template rendering without Kubernetes access (dry-run, testing).
     *
     * @return Map of function name to Function implementation with stub behavior
     */
    public static Map<String, Function> getFunctions() {
        return getFunctions(null);
    }

    /**
     * lookup queries Kubernetes for resource information.
     * <p>
     * Syntax: lookup "apiVersion" "kind" "namespace" "name"
     * <p>
     * Examples:
     * - lookup "v1" "Pod" "default" "mypod"
     * - lookup "v1" "Secret" "kube-system" "my-secret"
     * - lookup "apps/v1" "Deployment" "production" "web-app"
     * <p>
     * If name is empty, returns list of resources in the namespace.
     * If namespace is empty, uses "default" namespace.
     *
     * @param provider KubernetesProvider for API access (null for stub)
     * @return Function that performs Kubernetes lookups
     */
    private static Function lookup(KubernetesProvider provider) {
        return args -> {
            if (provider == null || !provider.isAvailable()) {
                // Stub implementation - returns empty map when no provider
                return Map.of();
            }

            if (args.length < 4) {
                throw new RuntimeException("lookup requires 4 arguments: apiVersion, kind, namespace, name");
            }

            String apiVersion = String.valueOf(args[0]);
            String kind = String.valueOf(args[1]);
            String namespace = String.valueOf(args[2]);
            String name = String.valueOf(args[3]);

            return provider.lookup(apiVersion, kind, namespace, name);
        };
    }

    /**
     * kubeVersion returns Kubernetes cluster version information.
     * <p>
     * Returns a map with version details:
     * - Major: Major version number
     * - Minor: Minor version number
     * - GitVersion: Full git version (e.g., "v1.28.0")
     * - GitCommit: Git commit hash
     * - Platform: Platform string (e.g., "linux/amd64")
     * <p>
     * Example usage in templates:
     * {{ if semverCompare ">=1.28.0" .Capabilities.KubeVersion.GitVersion }}
     *
     * @param provider KubernetesProvider for API access (null for stub)
     * @return Function that returns version information
     */
    private static Function kubeVersion(KubernetesProvider provider) {
        return args -> {
            if (provider == null || !provider.isAvailable()) {
                // Stub implementation - returns default version
                Map<String, Object> version = new HashMap<>();
                version.put("Major", "1");
                version.put("Minor", "28");
                version.put("GitVersion", "v1.28.0");
                return version;
            }

            return provider.getVersion();
        };
    }
}
