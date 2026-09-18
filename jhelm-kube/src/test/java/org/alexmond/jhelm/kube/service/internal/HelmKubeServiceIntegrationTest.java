package org.alexmond.jhelm.kube.service.internal;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import org.alexmond.jhelm.core.model.Capabilities;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.alexmond.jhelm.kube.KubeClusterAvailable;
import org.alexmond.jhelm.kube.KubernetesConfig;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = { KubernetesConfig.class, HelmKubeService.class })
@KubeClusterAvailable
class HelmKubeServiceIntegrationTest {

	@Autowired
	private HelmKubeService helmKubeService;

	@Autowired
	private KubeClient kubeClient;

	@Test
	void testListPodsInKubeSystem() throws ApiException {
		List<String> pods = helmKubeService.listPods("kube-system");
		assertNotNull(pods);
		System.out.println("Found pods in kube-system: " + pods);
	}

	@Test
	void testInstallConfigMap() throws ApiException {
		String yaml = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: jhelm-test-cm
				data:
				  key: value
				""";

		helmKubeService.installConfigMap("default", yaml);

		// Verify
		ApiClient apiClient = kubeClient.apiClient();
		CoreV1Api api = new CoreV1Api(apiClient);
		V1ConfigMap cm = api.readNamespacedConfigMap("jhelm-test-cm", "default").execute();
		assertNotNull(cm);
		assertEquals("value", cm.getData().get("key"));

		// Cleanup
		api.deleteNamespacedConfigMap("jhelm-test-cm", "default");
	}

	@Test
	void testUpgradeWithPruneRemovesDroppedField() throws ApiException {
		String previous = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: jhelm-prune-cm
				data:
				  keep: "1"
				  drop: "2"
				""";
		String upgraded = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: jhelm-prune-cm
				data:
				  keep: "1"
				""";

		helmKubeService.apply("default", previous);
		// Upgrade drops the "drop" key; three-way prune must delete it from the live
		// object.
		helmKubeService.applyWithPrune("default", previous, upgraded);

		CoreV1Api api = new CoreV1Api(kubeClient.apiClient());
		V1ConfigMap cm = api.readNamespacedConfigMap("jhelm-prune-cm", "default").execute();
		assertNotNull(cm);
		assertEquals("1", cm.getData().get("keep"));
		assertFalse(cm.getData().containsKey("drop"), "dropped data key must be pruned on upgrade (#814)");

		// Cleanup
		api.deleteNamespacedConfigMap("jhelm-prune-cm", "default");
	}

	@Test
	void getCapabilities_honoursDefaultContract() {
		// Mirror of the Fabric8 backend's test so both backends are covered symmetrically
		// (#819). A null kubeVersion is the documented DEFAULT ("use the engine's
		// built-in
		// default") returned when the cluster's /version endpoint is unreadable; a
		// non-null
		// version must be real and non-blank.
		Capabilities caps = helmKubeService.getCapabilities();
		assertNotNull(caps);
		if (caps.kubeVersion() != null) {
			assertFalse(caps.kubeVersion().isBlank());
		}
	}

}
