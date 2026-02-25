package org.alexmond.jhelm.kube;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class KubernetesConfigTest {

	@Test
	void testKubernetesConfigInstantiation() {
		KubernetesConfig config = new KubernetesConfig();
		assertNotNull(config);
	}

}
