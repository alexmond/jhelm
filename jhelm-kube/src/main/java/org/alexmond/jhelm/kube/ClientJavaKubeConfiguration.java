package org.alexmond.jhelm.kube;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.alexmond.jhelm.kube.service.internal.AsyncHelmKubeService;
import org.alexmond.jhelm.kube.service.internal.KubeClient;
import org.alexmond.jhelm.kube.service.internal.KubernetesClientProvider;
import org.alexmond.jhelm.kube.service.internal.KubernetesHealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kubernetes backend backed by the official Kubernetes Java client
 * ({@code io.kubernetes:client-java}). Guarded by {@link ConditionalOnClass} on
 * {@link ApiClient} so the entire configuration — and the client types it references — is
 * only introspected when that client is on the classpath. This lets jhelm ship a second
 * backend gated on a different client library and select between them by whichever client
 * the consumer puts on the classpath.
 *
 * <p>
 * Imported by {@link JhelmKubeAutoConfiguration}, which owns the module-wide,
 * client-agnostic concerns (properties, decorator chain).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ApiClient.class)
class ClientJavaKubeConfiguration {

	/**
	 * Registers the {@link KubeClient} used by the module. If a consumer supplies their
	 * own {@link ApiClient} bean (the intentional, documented customization seam) it is
	 * wrapped as-is; otherwise a client is built from the configured kubeconfig path,
	 * falling back to the default in-cluster or local client.
	 * @param props the Kubernetes configuration properties, providing the optional
	 * kubeconfig path
	 * @param apiClientOverride provider for an optional consumer-supplied API client used
	 * to override the built-in client
	 * @return the registered {@link KubeClient}
	 * @throws IOException if the configured kubeconfig file cannot be read
	 */
	@Bean
	@ConditionalOnMissingBean
	KubeClient kubeClient(JhelmKubernetesProperties props, ObjectProvider<ApiClient> apiClientOverride)
			throws IOException {
		ApiClient override = apiClientOverride.getIfAvailable();
		ApiClient client = (override != null) ? override : buildApiClient(props);
		return new KubeClient(client);
	}

	/**
	 * Registers the cluster-backed {@link KubernetesProvider} that powers the
	 * {@code lookup} template function. The core {@code Engine} wires this in so
	 * {@code lookup} queries the live Kubernetes API (returning full resource data, incl.
	 * Secret/ConfigMap {@code data}) during install/upgrade, as Helm does. It degrades to
	 * an empty result on its own when the API is unreachable (e.g. offline
	 * {@code template} rendering).
	 * @param kubeClient the shared Kubernetes client
	 * @return the lookup provider bean
	 */
	@Bean
	@ConditionalOnMissingBean(KubernetesProvider.class)
	KubernetesProvider kubernetesClientProvider(KubeClient kubeClient) {
		return new KubernetesClientProvider(kubeClient);
	}

	/**
	 * Builds the {@link KubeService} bean from the official-client base implementation,
	 * applying the module-wide decorator chain (retry, metrics) owned by
	 * {@link JhelmKubeAutoConfiguration}.
	 * @param kubeClient the registered Kubernetes client wrapper
	 * @param props the Kubernetes configuration properties, providing the retry settings
	 * @param metricsProvider provider for the optional metrics bean used to enable
	 * operation timing and counting
	 * @return the (possibly decorated) Kubernetes service
	 */
	@Bean
	@ConditionalOnMissingBean(KubeService.class)
	KubeService kubeService(KubeClient kubeClient, JhelmKubernetesProperties props,
			ObjectProvider<JhelmMetrics> metricsProvider) {
		AsyncHelmKubeService base = new AsyncHelmKubeService(kubeClient);
		return KubeServiceDecorators.decorate(base, props, metricsProvider);
	}

	/**
	 * Builds the Kubernetes {@link ApiClient} used by the module. When a kubeconfig path
	 * is configured the client is built from that file, otherwise the default in-cluster
	 * or local client is used.
	 * @param props the Kubernetes configuration properties, providing the optional
	 * kubeconfig path
	 * @return the configured Kubernetes API client
	 * @throws IOException if the configured kubeconfig file cannot be read
	 */
	private ApiClient buildApiClient(JhelmKubernetesProperties props) throws IOException {
		ApiClient client;
		String kubeconfigPath = props.getKubeconfigPath();
		String context = props.getContext();
		if (kubeconfigPath != null || (context != null && !context.isBlank())) {
			// A configured kubeconfig path, or an explicit --kube-context, means load the
			// kubeconfig ourselves so the requested context can be selected.
			String path = (kubeconfigPath != null) ? kubeconfigPath : defaultKubeconfigPath();
			try (FileReader reader = new FileReader(path, StandardCharsets.UTF_8)) {
				KubeConfig kubeConfig = KubeConfig.loadKubeConfig(reader);
				if (context != null && !context.isBlank() && !kubeConfig.setContext(context)) {
					throw new IOException("kube context not found in kubeconfig: " + context);
				}
				client = Config.fromConfig(kubeConfig);
			}
		}
		else {
			client = Config.defaultClient();
		}
		if (props.getApiServer() != null && !props.getApiServer().isBlank()) {
			client.setBasePath(props.getApiServer());
		}
		return client;
	}

	// The kubeconfig the Kubernetes client would auto-detect: the first entry of
	// $KUBECONFIG, else ~/.kube/config. Used only when a context is requested without an
	// explicit --kubeconfig.
	private static String defaultKubeconfigPath() {
		String env = System.getenv("KUBECONFIG");
		if (env != null && !env.isBlank()) {
			return env.split(File.pathSeparator, 2)[0];
		}
		return System.getProperty("user.home") + "/.kube/config";
	}

	/**
	 * Kubernetes health-indicator configuration, isolated in a nested class guarded by
	 * {@link ConditionalOnClass} so the actuator {@code HealthIndicator} type is only
	 * referenced when Spring Boot Actuator is on the classpath. This keeps the enclosing
	 * configuration introspectable by consumers (e.g. the CLI) that do not depend on
	 * actuator.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HealthIndicator.class)
	static class KubernetesHealthContributorConfiguration {

		/**
		 * Registers a Kubernetes connectivity health indicator reporting up/down based on
		 * reaching the cluster {@code /version} endpoint.
		 * @param kubeClient the client wrapper to probe
		 * @return the health indicator bean
		 */
		@Bean
		@ConditionalOnBean(KubeClient.class)
		@ConditionalOnMissingBean(KubernetesHealthIndicator.class)
		KubernetesHealthIndicator kubernetesHealthIndicator(KubeClient kubeClient) {
			return new KubernetesHealthIndicator(kubeClient);
		}

	}

}
