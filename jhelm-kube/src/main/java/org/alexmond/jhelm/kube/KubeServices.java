package org.alexmond.jhelm.kube;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.kubernetes.client.openapi.ApiClient;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.alexmond.jhelm.kube.service.internal.AsyncHelmKubeService;
import org.alexmond.jhelm.kube.service.internal.Fabric8AsyncKubeService;
import org.alexmond.jhelm.kube.service.internal.KubeClient;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Factory for building a fully-decorated {@link KubeService} from a Kubernetes client the
 * caller already owns. This is the supported way for a host that manages its own clients
 * (for example a multi-cluster host holding one client per cluster) to obtain a
 * {@link KubeService} that behaves identically to the auto-configured one — the same
 * module-wide decorator chain (retry, metrics) is applied — without depending on the
 * auto-configuration or on any {@code ...service.internal} type.
 *
 * <p>
 * Pair this with the {@code KubeServiceResolver} multi-cluster routing seam (issue #773):
 * build one {@link KubeService} per cluster here, then register each with the resolver so
 * requests are routed to the right cluster.
 *
 * <p>
 * Two client backends are supported: the Fabric8 client ({@link #fabric8}) and the
 * official Kubernetes Java client ({@link #clientJava}). Each has a convenience overload
 * that uses default properties (retry enabled) and no metrics, and a full-control
 * overload accepting explicit {@link JhelmKubernetesProperties} and a nullable
 * {@link JhelmMetrics}.
 */
public final class KubeServices {

	private KubeServices() {
	}

	/**
	 * Builds a decorated {@link KubeService} from a Fabric8 client, using default
	 * properties (retry enabled) and no metrics.
	 * @param client the Fabric8 Kubernetes client to back the service
	 * @return the decorated Kubernetes service
	 */
	public static KubeService fabric8(KubernetesClient client) {
		return fabric8(client, new JhelmKubernetesProperties(), null);
	}

	/**
	 * Builds a decorated {@link KubeService} from a Fabric8 client with full control over
	 * the decorator chain.
	 * @param client the Fabric8 Kubernetes client to back the service
	 * @param props the Kubernetes configuration properties, providing the retry settings
	 * @param metrics the metrics used to enable operation timing and counting, or
	 * {@code null} to disable metrics
	 * @return the (possibly decorated) Kubernetes service
	 */
	public static KubeService fabric8(KubernetesClient client, JhelmKubernetesProperties props, JhelmMetrics metrics) {
		return KubeServiceDecorators.decorate(new Fabric8AsyncKubeService(client), props, metricsProvider(metrics));
	}

	/**
	 * Builds a decorated {@link KubeService} from an official-client {@link ApiClient},
	 * using default properties (retry enabled) and no metrics.
	 * @param apiClient the official Kubernetes Java client to back the service
	 * @return the decorated Kubernetes service
	 */
	public static KubeService clientJava(ApiClient apiClient) {
		return clientJava(apiClient, new JhelmKubernetesProperties(), null);
	}

	/**
	 * Builds a decorated {@link KubeService} from an official-client {@link ApiClient}
	 * with full control over the decorator chain.
	 * @param apiClient the official Kubernetes Java client to back the service
	 * @param props the Kubernetes configuration properties, providing the retry settings
	 * @param metrics the metrics used to enable operation timing and counting, or
	 * {@code null} to disable metrics
	 * @return the (possibly decorated) Kubernetes service
	 */
	public static KubeService clientJava(ApiClient apiClient, JhelmKubernetesProperties props, JhelmMetrics metrics) {
		AsyncHelmKubeService base = new AsyncHelmKubeService(new KubeClient(apiClient));
		return KubeServiceDecorators.decorate(base, props, metricsProvider(metrics));
	}

	/**
	 * Adapts a nullable {@link JhelmMetrics} to the {@link ObjectProvider} expected by
	 * {@link KubeServiceDecorators#decorate}, which enables metrics only when
	 * {@link ObjectProvider#getIfAvailable()} returns a non-null instance.
	 * @param metrics the metrics instance, or {@code null}
	 * @return a provider whose {@code getIfAvailable()} yields the given instance
	 */
	private static ObjectProvider<JhelmMetrics> metricsProvider(JhelmMetrics metrics) {
		return new ObjectProvider<>() {
			@Override
			public JhelmMetrics getObject() {
				if (metrics == null) {
					throw new NoSuchBeanDefinitionException(JhelmMetrics.class);
				}
				return metrics;
			}

			@Override
			public JhelmMetrics getIfAvailable() {
				return metrics;
			}
		};
	}

}
