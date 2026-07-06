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
	 * Retry configuration for transient Kubernetes API failures.
	 */
	private Retry retry = new Retry();

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

}
