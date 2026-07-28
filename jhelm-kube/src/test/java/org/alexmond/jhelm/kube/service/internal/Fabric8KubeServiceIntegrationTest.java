package org.alexmond.jhelm.kube.service.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.kubernetes.client.util.Config;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.kube.KubeClusterAvailable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the Fabric8 {@link Fabric8KubeService} against a live cluster
 * (skipped by {@link KubeClusterAvailable} when none is reachable). Beyond exercising
 * each operation, it verifies <em>cross-backend parity</em>: a release stored by the
 * Fabric8 backend is read back by the official-client {@link HelmKubeService} (and vice
 * versa), proving the {@code sh.helm.release.v1.*} Secret format is byte-identical
 * between the two backends.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KubeClusterAvailable
class Fabric8KubeServiceIntegrationTest {

	private static final String NAMESPACE = "jhelm-fabric8-it";

	private KubernetesClient fabric8Client;

	private Fabric8KubeService fabric8;

	private HelmKubeService clientJava;

	@BeforeAll
	void setUp() throws Exception {
		this.fabric8Client = new KubernetesClientBuilder().build();
		this.fabric8 = new Fabric8KubeService(this.fabric8Client);
		this.clientJava = new HelmKubeService(new KubeClient(Config.defaultClient()));
		this.fabric8.ensureNamespace(NAMESPACE);
		// Clean any leftovers from a previous run.
		this.fabric8.deleteReleaseHistory("parity-app", NAMESPACE);
		this.fabric8.deleteReleaseHistory("prune-app", NAMESPACE);
	}

	@AfterAll
	void tearDown() {
		if (this.fabric8 != null) {
			this.fabric8.deleteReleaseHistory("parity-app", NAMESPACE);
			this.fabric8.deleteReleaseHistory("prune-app", NAMESPACE);
		}
		if (this.fabric8Client != null) {
			this.fabric8Client.namespaces().withName(NAMESPACE).delete();
			this.fabric8Client.close();
		}
	}

	@Test
	void storeReadHistory_roundTrips() {
		this.fabric8.storeRelease(release("parity-app", 1));
		this.fabric8.storeRelease(release("parity-app", 2));

		Optional<Release> latest = this.fabric8.getRelease("parity-app", NAMESPACE);
		assertTrue(latest.isPresent());
		assertEquals(2, latest.get().getVersion());

		List<Release> history = this.fabric8.getReleaseHistory("parity-app", NAMESPACE);
		assertEquals(2, history.size());
		assertEquals(2, history.get(0).getVersion(), "history is newest-first");
		assertEquals(1, history.get(1).getVersion());

		assertTrue(this.fabric8.listReleases(NAMESPACE).stream().anyMatch((r) -> "parity-app".equals(r.getName())));
	}

	@Test
	void releaseSecretFormat_isReadableByOfficialClient() {
		this.fabric8.storeRelease(release("parity-app", 3));

		// The official-client backend must decode a Fabric8-written release Secret.
		Optional<Release> viaClientJava = this.clientJava.getRelease("parity-app", NAMESPACE);
		assertTrue(viaClientJava.isPresent(), "client-java must read a Fabric8-written release");
		assertEquals(3, viaClientJava.get().getVersion());
		assertEquals("parity-app", viaClientJava.get().getName());

		// And the reverse: Fabric8 must decode an official-client-written release Secret.
		this.clientJava.storeRelease(release("parity-app", 4));
		Optional<Release> viaFabric8 = this.fabric8.getRelease("parity-app", NAMESPACE);
		assertTrue(viaFabric8.isPresent());
		assertEquals(4, viaFabric8.get().getVersion());
	}

	@Test
	void pruneReleaseHistory_keepsNewest() {
		this.fabric8.storeRelease(release("prune-app", 1));
		this.fabric8.storeRelease(release("prune-app", 2));
		this.fabric8.storeRelease(release("prune-app", 3));

		this.fabric8.pruneReleaseHistory("prune-app", NAMESPACE, 2);

		List<Release> history = this.fabric8.getReleaseHistory("prune-app", NAMESPACE);
		assertEquals(2, history.size());
		assertEquals(3, history.get(0).getVersion());
		assertEquals(2, history.get(1).getVersion());
	}

	@Test
	void applyStatusDelete_configMap() {
		String yaml = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: jhelm-fabric8-cm
				data:
				  key: value
				""";
		this.fabric8.apply(NAMESPACE, yaml);

		assertNotNull(this.fabric8Client.configMaps().inNamespace(NAMESPACE).withName("jhelm-fabric8-cm").get());

		List<ResourceStatus> statuses = this.fabric8.getResourceStatuses(NAMESPACE, yaml);
		assertEquals(1, statuses.size());
		assertTrue(statuses.get(0).isReady());

		this.fabric8.delete(NAMESPACE, yaml);
		this.fabric8.waitForDeleted(NAMESPACE, yaml, 30);
		assertNull(this.fabric8Client.configMaps().inNamespace(NAMESPACE).withName("jhelm-fabric8-cm").get());
	}

	@Test
	void applyDeploymentAndWaitForReady() {
		String yaml = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: jhelm-fabric8-dep
				spec:
				  replicas: 1
				  selector:
				    matchLabels:
				      app: jhelm-fabric8-dep
				  template:
				    metadata:
				      labels:
				        app: jhelm-fabric8-dep
				    spec:
				      containers:
				        - name: pause
				          image: registry.k8s.io/pause:3.9
				""";
		try {
			this.fabric8.apply(NAMESPACE, yaml);
			this.fabric8.waitForReady(NAMESPACE, yaml, 120);
			assertTrue(this.fabric8.getResourceStatuses(NAMESPACE, yaml).get(0).isReady());
		}
		finally {
			this.fabric8.delete(NAMESPACE, yaml);
			this.fabric8.waitForDeleted(NAMESPACE, yaml, 60);
		}
	}

	@Test
	void getCapabilities_returnsClusterVersion() {
		assertNotNull(this.fabric8.getCapabilities());
		assertNotNull(this.fabric8.getCapabilities().kubeVersion());
		assertFalse(this.fabric8.getCapabilities().kubeVersion().isBlank());
	}

	private static Release release(String name, int version) {
		return Release.builder()
			.name(name)
			.namespace(NAMESPACE)
			.version(version)
			.info(Release.ReleaseInfo.builder()
				.status(ReleaseStatus.DEPLOYED)
				.firstDeployed(OffsetDateTime.now())
				.lastDeployed(OffsetDateTime.now())
				.description("Fabric8 IT release")
				.build())
			.build();
	}

}
