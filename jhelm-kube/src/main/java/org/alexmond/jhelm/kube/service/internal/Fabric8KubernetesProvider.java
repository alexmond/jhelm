package org.alexmond.jhelm.kube.service.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;

/**
 * {@link KubernetesProvider} implementation backed by the Fabric8 Kubernetes client. It
 * mirrors the official-client provider: real cluster access for the {@code lookup} and
 * {@code kubeVersion} template functions, degrading to empty results or stub version
 * information when the API server is unreachable.
 *
 * <p>
 * Resources are fetched through Fabric8's generic (unstructured) API, which discovers the
 * resource mapping from {@code apiVersion} + {@code kind}, and serialized with the
 * client's own {@code KubernetesSerialization} so field names match the wire format and
 * {@code Secret.data} values stay base64-encoded — matching Helm's {@code lookup}.
 */
@Slf4j
public class Fabric8KubernetesProvider implements KubernetesProvider {

	private final KubernetesClient client;

	private volatile Boolean available;

	/**
	 * Creates a provider backed by the given Fabric8 client.
	 * @param client the configured Fabric8 Kubernetes client used to look up resources
	 * and query cluster version information
	 */
	public Fabric8KubernetesProvider(KubernetesClient client) {
		this.client = client;
	}

	@Override
	public Map<String, Object> lookup(String apiVersion, String kind, String namespace, String name) {
		if (!isAvailable()) {
			if (log.isWarnEnabled()) {
				log.warn("Kubernetes API not available, returning empty result for lookup");
			}
			return Map.of();
		}
		try {
			if (log.isDebugEnabled()) {
				log.debug("Looking up Kubernetes resource: apiVersion={}, kind={}, namespace={}, name={}", apiVersion,
						kind, namespace, name);
			}
			boolean namespaced = namespace != null && !namespace.isBlank();
			var operation = this.client.genericKubernetesResources(apiVersion, kind);
			if (name == null || name.isEmpty()) {
				Object list = namespaced ? operation.inNamespace(namespace).list() : operation.inAnyNamespace().list();
				return toMap(list);
			}
			GenericKubernetesResource resource = namespaced ? operation.inNamespace(namespace).withName(name).get()
					: operation.withName(name).get();
			return (resource != null) ? toMap(resource) : Map.of();
		}
		catch (RuntimeException ex) {
			if (log.isErrorEnabled()) {
				log.error("Error looking up Kubernetes resource {}/{}: {}", apiVersion, kind, ex.getMessage(), ex);
			}
			return Map.of();
		}
	}

	@Override
	public Map<String, Object> getVersion() {
		if (!isAvailable()) {
			if (log.isWarnEnabled()) {
				log.warn("Kubernetes API not available, returning stub version info");
			}
			return getStubVersion();
		}
		try {
			VersionInfo info = this.client.getKubernetesVersion();
			Map<String, Object> version = new LinkedHashMap<>();
			version.put("Major", info.getMajor());
			version.put("Minor", info.getMinor());
			version.put("GitVersion", info.getGitVersion());
			version.put("GitCommit", info.getGitCommit());
			version.put("Platform", info.getPlatform());
			version.put("BuildDate", info.getBuildDate());
			return version;
		}
		catch (RuntimeException ex) {
			if (log.isErrorEnabled()) {
				log.error("Error fetching Kubernetes version: {}", ex.getMessage(), ex);
			}
			return getStubVersion();
		}
	}

	@Override
	public boolean isAvailable() {
		if (this.available != null) {
			return this.available;
		}
		try {
			this.client.getKubernetesVersion();
			this.available = true;
			if (log.isInfoEnabled()) {
				log.info("Kubernetes API is available");
			}
			return true;
		}
		catch (RuntimeException ex) {
			this.available = false;
			if (log.isWarnEnabled()) {
				log.warn("Kubernetes API is not available: {}", ex.getMessage());
			}
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toMap(Object resource) {
		if (resource == null) {
			return Map.of();
		}
		Object converted = this.client.getKubernetesSerialization().convertValue(resource, Map.class);
		if (converted instanceof Map) {
			return (Map<String, Object>) converted;
		}
		Map<String, Object> wrapper = new LinkedHashMap<>();
		wrapper.put("value", converted);
		return wrapper;
	}

	/**
	 * Return stub version when the Kubernetes API is not available. Kept aligned with the
	 * engine's default {@code .Capabilities.KubeVersion} ({@code v1.35.0}) so the
	 * {@code kubeVersion} template function and {@code .Capabilities.KubeVersion} agree
	 * when no live cluster is reachable.
	 */
	private Map<String, Object> getStubVersion() {
		Map<String, Object> version = new LinkedHashMap<>();
		version.put("Major", "1");
		version.put("Minor", "35");
		version.put("GitVersion", "v1.35.0");
		version.put("GitCommit", "unknown");
		version.put("Platform", "linux/amd64");
		return version;
	}

}
