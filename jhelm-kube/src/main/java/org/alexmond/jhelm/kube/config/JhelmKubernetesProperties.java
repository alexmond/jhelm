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
	 * Path to the kubeconfig file. When not set, the Kubernetes client uses its standard
	 * auto-detection: {@code ~/.kube/config} or in-cluster service account credentials.
	 */
	private String kubeconfigPath;

	/**
	 * Retry configuration for transient Kubernetes API failures.
	 */
	private Retry retry = new Retry();

	@Getter
	@Setter
	public static class Retry {

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
