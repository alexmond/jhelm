package org.alexmond.jhelm.kube;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.alexmond.jhelm.kube.service.internal.Fabric8KubernetesProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kubernetes backend backed by the Fabric8 Kubernetes client
 * ({@code io.fabric8:kubernetes-client}). Guarded by {@link ConditionalOnClass} on
 * {@link KubernetesClient} — so the entire configuration and its client types are only
 * introspected when Fabric8 is on the classpath — and by {@link ConditionalOnKubeBackend}
 * so it activates only when Fabric8 is the selected backend (see
 * {@link OnKubeBackendCondition}).
 *
 * <p>
 * This slice provides the client and the cluster-backed {@link KubernetesProvider} (the
 * {@code lookup}/{@code kubeVersion} SPI). The full {@code KubeService} implementation
 * (install/upgrade/uninstall cluster operations) is added in a later slice; until then a
 * Fabric8-only classpath renders templates and resolves {@code lookup}, but does not yet
 * perform releases.
 *
 * <p>
 * Imported by {@link JhelmKubeAutoConfiguration}, which owns the module-wide,
 * client-agnostic concerns.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KubernetesClient.class)
@ConditionalOnKubeBackend(KubeBackend.FABRIC8)
class Fabric8KubeConfiguration {

	/**
	 * Registers the Fabric8 {@link KubernetesClient}. Consumers may supply their own
	 * {@link KubernetesClient} bean (the customization seam), in which case this one
	 * backs off; otherwise a client is built from the configured kubeconfig path /
	 * context, falling back to Fabric8's default auto-configuration (in-cluster or
	 * {@code ~/.kube/config}).
	 * @param props the Kubernetes configuration properties
	 * @return the Fabric8 client
	 * @throws IOException if the configured kubeconfig file cannot be read
	 */
	@Bean
	@ConditionalOnMissingBean
	KubernetesClient kubernetesClient(JhelmKubernetesProperties props) throws IOException {
		return new KubernetesClientBuilder().withConfig(buildConfig(props)).build();
	}

	/**
	 * Registers the cluster-backed {@link KubernetesProvider} that powers the
	 * {@code lookup} template function, backed by the Fabric8 client.
	 * @param client the Fabric8 client
	 * @return the lookup provider bean
	 */
	@Bean
	@ConditionalOnMissingBean(KubernetesProvider.class)
	KubernetesProvider kubernetesClientProvider(KubernetesClient client) {
		return new Fabric8KubernetesProvider(client);
	}

	private Config buildConfig(JhelmKubernetesProperties props) throws IOException {
		Config config;
		String kubeconfigPath = props.getKubeconfigPath();
		String context = props.getContext();
		if (kubeconfigPath != null || (context != null && !context.isBlank())) {
			// A configured kubeconfig path, or an explicit --kube-context, means load the
			// kubeconfig ourselves so the requested context can be selected.
			String path = (kubeconfigPath != null) ? kubeconfigPath : defaultKubeconfigPath();
			String contents = Files.readString(Path.of(path), StandardCharsets.UTF_8);
			config = Config.fromKubeconfig(context, contents, path);
		}
		else {
			config = Config.autoConfigure(null);
		}
		if (props.getApiServer() != null && !props.getApiServer().isBlank()) {
			config.setMasterUrl(props.getApiServer());
		}
		return config;
	}

	// The kubeconfig Fabric8 would auto-detect: the first entry of $KUBECONFIG, else
	// ~/.kube/config. Used only when a context is requested without an explicit
	// --kubeconfig.
	private static String defaultKubeconfigPath() {
		String env = System.getenv("KUBECONFIG");
		if (env != null && !env.isBlank()) {
			return env.split(File.pathSeparator, 2)[0];
		}
		return System.getProperty("user.home") + "/.kube/config";
	}

}
