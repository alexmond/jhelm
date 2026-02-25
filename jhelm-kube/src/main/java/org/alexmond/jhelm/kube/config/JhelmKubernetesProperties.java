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

}
