package org.alexmond.jhelm.kube;

/**
 * The Kubernetes client backends jhelm can run on. Declaration order is the preference
 * order used when {@code jhelm.kubernetes.backend=auto} and more than one backend's
 * client library is on the classpath: the first available wins, so {@link #CLIENT_JAVA}
 * is preferred over {@link #FABRIC8} to preserve jhelm's historical default.
 */
public enum KubeBackend {

	/** The official Kubernetes Java client, {@code io.kubernetes:client-java}. */
	CLIENT_JAVA("client-java", "io.kubernetes.client.openapi.ApiClient"),

	/** The Fabric8 Kubernetes client, {@code io.fabric8:kubernetes-client}. */
	FABRIC8("fabric8", "io.fabric8.kubernetes.client.KubernetesClient");

	private final String id;

	private final String markerClass;

	KubeBackend(String id, String markerClass) {
		this.id = id;
		this.markerClass = markerClass;
	}

	/**
	 * Returns the value used in the {@code jhelm.kubernetes.backend} property to select
	 * this backend explicitly (e.g. {@code client-java}, {@code fabric8}).
	 * @return the property token for this backend
	 */
	public String id() {
		return this.id;
	}

	/**
	 * Returns the fully qualified name of the client class whose presence on the
	 * classpath indicates this backend's library is available.
	 * @return the marker class name
	 */
	public String markerClass() {
		return this.markerClass;
	}

}
