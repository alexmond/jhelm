package org.alexmond.jhelm.kube.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jhelm Kubernetes integration module.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.kubernetes")
public class JhelmKubernetesProperties {

	/**
	 * Creates the properties holder with default values.
	 */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public JhelmKubernetesProperties() {
	}

	/**
	 * Path to the kubeconfig file. When not set, the Kubernetes client uses its standard
	 * auto-detection: {@code ~/.kube/config} or in-cluster service account credentials.
	 */
	private String kubeconfigPath;

	/**
	 * Name of the kubeconfig context to use (Helm's {@code --kube-context}). When set,
	 * the kubeconfig is loaded (from {@link #kubeconfigPath}, {@code $KUBECONFIG}, or
	 * {@code ~/.kube/config}) and this context is selected. When not set, the
	 * kubeconfig's current context is used.
	 */
	private String context;

	/**
	 * Address of the Kubernetes API server to talk to (Helm's {@code --kube-apiserver}).
	 * When set, it overrides the server resolved from the kubeconfig. When not set, the
	 * kubeconfig's server is used.
	 */
	private String apiServer;

	/**
	 * Selects the Kubernetes client backend. {@code auto} (the default) picks the
	 * preferred backend among those on the classpath (the official {@code client-java}
	 * over Fabric8 when both are present); {@code client-java} or {@code fabric8} force a
	 * specific backend, which then requires that client library to be present. jhelm
	 * ships neither client itself — the consumer puts one on the classpath. See
	 * {@code ClientJavaKubeConfiguration} / {@code Fabric8KubeConfiguration}.
	 */
	private String backend = "auto";

	/**
	 * Retry configuration for transient Kubernetes API failures.
	 */
	private Retry retry = new Retry();

	/**
	 * Kubernetes health indicator configuration.
	 */
	private Health health = new Health();

	/**
	 * Retry configuration for transient Kubernetes API failures, controlling the maximum
	 * number of attempts and the exponential backoff between them.
	 */
	@Getter
	@Setter
	public static class Retry {

		/**
		 * Creates the retry settings with default values.
		 */
		@SuppressWarnings("PMD.UnnecessaryConstructor")
		public Retry() {
		}

		/**
		 * Whether retry is enabled for Kubernetes API calls.
		 */
		private boolean enabled = true;

		/**
		 * Maximum number of retry attempts (including the initial call).
		 */
		private int maxAttempts = 3;

		/**
		 * Initial interval between retries in milliseconds.
		 */
		private long initialIntervalMs = 1000;

		/**
		 * Multiplier for exponential backoff between retries.
		 */
		private double multiplier = 2.0;

		/**
		 * Maximum interval between retries in milliseconds.
		 */
		private long maxIntervalMs = 10000;

	}

	/**
	 * Health indicator configuration for the Kubernetes integration. Controls whether the
	 * {@code KubernetesHealthIndicator} — which probes the ambient client's cluster
	 * connectivity and contributes to the aggregate {@code /actuator/health} — is
	 * registered when Spring Boot Actuator is on the classpath. A host that embeds jhelm
	 * but does not want jhelm's single-cluster health folded into its own aggregate (e.g.
	 * a multi-cluster application) can turn it off even when an ambient client exists.
	 */
	@Getter
	@Setter
	public static class Health {

		/**
		 * Creates the health settings with default values.
		 */
		@SuppressWarnings("PMD.UnnecessaryConstructor")
		public Health() {
		}

		/**
		 * Whether the Kubernetes health indicator is registered. Defaults to
		 * {@code true}; set to {@code false} to opt out even when a Kubernetes client is
		 * present.
		 */
		private boolean enabled = true;

	}

}
