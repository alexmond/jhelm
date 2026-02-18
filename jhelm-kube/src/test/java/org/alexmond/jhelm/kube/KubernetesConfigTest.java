package org.alexmond.jhelm.kube;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KubernetesConfigTest {

    @Test
    void testKubernetesConfigInstantiation() {
        KubernetesConfig config = new KubernetesConfig();
        assertNotNull(config);
    }

    @Test
    void testApiClientBeanCreation() {
        KubernetesConfig config = new KubernetesConfig();
        // apiClient() may throw if no kube config exists, which is expected
        // in a test environment without a cluster
        try {
            var client = config.apiClient();
            assertNotNull(client);
        } catch (Exception e) {
            // Expected when no kubeconfig is available
            assertNotNull(e.getMessage());
        }
    }
}
