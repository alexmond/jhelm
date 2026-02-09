package org.alexmond.jhelm.kube;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {KubernetesConfig.class, HelmKubeService.class})
class HelmKubeServiceIntegrationTest {

    @Autowired
    private HelmKubeService helmKubeService;

    @Autowired
    private ApiClient apiClient;

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
        CoreV1Api api = new CoreV1Api(apiClient); 
        V1ConfigMap cm = api.readNamespacedConfigMap("jhelm-test-cm", "default").execute();
        assertNotNull(cm);
        assertEquals("value", cm.getData().get("key"));
        
        // Cleanup
        api.deleteNamespacedConfigMap("jhelm-test-cm", "default");
    }
}
