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
import org.alexmond.jhelm.core.service.CascadePolicy;
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
 * Integration test for the Fabric8 backend against a live cluster (skipped by
 * {@link KubeClusterAvailable} when none is reachable). Exercises the full
 * {@link Fabric8AsyncKubeService} surface — sync and async — and verifies
 * <em>cross-backend parity</em>: a release stored by the Fabric8 backend is read back by
 * the official-client {@link HelmKubeService} (and vice versa), proving the
 * {@code sh.helm.release.v1.*} Secret format is byte-identical between the two backends.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KubeClusterAvailable
class Fabric8KubeServiceIntegrationTest {

	private static final String NAMESPACE = "jhelm-fabric8-it";

	private KubernetesClient fabric8Client;

	private Fabric8AsyncKubeService fabric8;

	private HelmKubeService clientJava;

	@BeforeAll
	void setUp() throws Exception {
		this.fabric8Client = new KubernetesClientBuilder().build();
		this.fabric8 = new Fabric8AsyncKubeService(this.fabric8Client);
		this.clientJava = new HelmKubeService(new KubeClient(Config.defaultClient()));
		// ensureNamespace twice exercises the create + already-exists (409) paths.
		this.fabric8.ensureNamespace(NAMESPACE);
		this.fabric8.ensureNamespace(NAMESPACE);
		this.fabric8.deleteReleaseHistory("parity-app", NAMESPACE);
		this.fabric8.deleteReleaseHistory("prune-app", NAMESPACE);
		this.fabric8.deleteReleaseHistory("async-app", NAMESPACE);
	}

	@AfterAll
	void tearDown() {
		if (this.fabric8 != null) {
			this.fabric8.deleteReleaseHistory("parity-app", NAMESPACE);
			this.fabric8.deleteReleaseHistory("prune-app", NAMESPACE);
			this.fabric8.deleteReleaseHistory("async-app", NAMESPACE);
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
		assertTrue(this.fabric8.listAllReleases().stream().anyMatch((r) -> "parity-app".equals(r.getName())));
	}

	@Test
	void releaseSecretFormat_isReadableByOfficialClient() {
		this.fabric8.storeRelease(release("parity-app", 3));

		Optional<Release> viaClientJava = this.clientJava.getRelease("parity-app", NAMESPACE);
		assertTrue(viaClientJava.isPresent(), "client-java must read a Fabric8-written release");
		assertEquals(3, viaClientJava.get().getVersion());
		assertEquals("parity-app", viaClientJava.get().getName());

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
	void applyDryRunStatusDelete_configMap() {
		String yaml = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: jhelm-fabric8-cm
				data:
				  key: value
				""";
		// dry-run must not persist.
		this.fabric8.applyDryRun(NAMESPACE, yaml);
		assertNull(this.fabric8Client.configMaps().inNamespace(NAMESPACE).withName("jhelm-fabric8-cm").get(),
				"server dry-run must not create the resource");

		this.fabric8.apply(NAMESPACE, yaml);
		assertNotNull(this.fabric8Client.configMaps().inNamespace(NAMESPACE).withName("jhelm-fabric8-cm").get());

		List<ResourceStatus> statuses = this.fabric8.getResourceStatuses(NAMESPACE, yaml);
		assertEquals(1, statuses.size());
		assertTrue(statuses.get(0).isReady());

		this.fabric8.delete(NAMESPACE, yaml, CascadePolicy.FOREGROUND);
		this.fabric8.waitForDeleted(NAMESPACE, yaml, 30);
		assertNull(this.fabric8Client.configMaps().inNamespace(NAMESPACE).withName("jhelm-fabric8-cm").get());
		// Deleting an already-absent resource is tolerated (no exception).
		this.fabric8.delete(NAMESPACE, yaml);
	}

	@Test
	void applyWithPrune_removesDroppedField() {
		String previous = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: jhelm-fabric8-prune-cm
				data:
				  keep: "1"
				  drop: "2"
				""";
		String upgraded = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: jhelm-fabric8-prune-cm
				data:
				  keep: "1"
				""";

		this.fabric8.apply(NAMESPACE, previous);
		// Upgrade drops the "drop" key; three-way prune must delete it from the live
		// object.
		this.fabric8.applyWithPrune(NAMESPACE, previous, upgraded);

		var cm = this.fabric8Client.configMaps().inNamespace(NAMESPACE).withName("jhelm-fabric8-prune-cm").get();
		assertNotNull(cm);
		assertEquals("1", cm.getData().get("keep"));
		assertFalse(cm.getData().containsKey("drop"), "dropped data key must be pruned on upgrade (#814)");

		this.fabric8.delete(NAMESPACE, upgraded);
	}

	@Test
	void getResourceStatuses_coversAllWorkloadKinds() {
		String yaml = allWorkloadsManifest();
		try {
			this.fabric8.apply(NAMESPACE, yaml);
			List<ResourceStatus> statuses = this.fabric8.getResourceStatuses(NAMESPACE, yaml);
			// One status per document: Deployment, ReplicaSet, DaemonSet, StatefulSet,
			// Job,
			// Pod, Service (unknown-to-the-switch → assumed ready).
			assertEquals(7, statuses.size());
			assertTrue(statuses.stream().anyMatch((s) -> "Deployment".equals(s.getKind())));
			assertTrue(statuses.stream().anyMatch((s) -> "StatefulSet".equals(s.getKind())));
			assertTrue(statuses.stream().anyMatch((s) -> "Job".equals(s.getKind())));
			assertTrue(statuses.stream().anyMatch((s) -> "Pod".equals(s.getKind())));
			// Service is not a workload kind, so it is reported ready by default.
			assertTrue(
					statuses.stream().filter((s) -> "Service".equals(s.getKind())).allMatch(ResourceStatus::isReady));
		}
		finally {
			this.fabric8.delete(NAMESPACE, yaml);
		}
	}

	@Test
	void restartWorkloads_stampsAnnotation() {
		String yaml = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: jhelm-fabric8-restart
				spec:
				  replicas: 1
				  selector:
				    matchLabels:
				      app: jhelm-fabric8-restart
				  template:
				    metadata:
				      labels:
				        app: jhelm-fabric8-restart
				    spec:
				      containers:
				        - name: pause
				          image: registry.k8s.io/pause:3.9
				""";
		try {
			this.fabric8.apply(NAMESPACE, yaml);
			this.fabric8.restartWorkloads(NAMESPACE, yaml);
			var dep = this.fabric8Client.apps()
				.deployments()
				.inNamespace(NAMESPACE)
				.withName("jhelm-fabric8-restart")
				.get();
			assertNotNull(dep.getSpec().getTemplate().getMetadata().getAnnotations());
			assertTrue(dep.getSpec()
				.getTemplate()
				.getMetadata()
				.getAnnotations()
				.containsKey("kubectl.kubernetes.io/restartedAt"));
		}
		finally {
			this.fabric8.delete(NAMESPACE, yaml);
			this.fabric8.waitForDeleted(NAMESPACE, yaml, 60);
		}
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
	void asyncOperations_roundTrip() throws Exception {
		String yaml = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: jhelm-fabric8-async-cm
				data:
				  key: value
				""";
		this.fabric8.storeReleaseAsync(release("async-app", 1)).get();
		assertTrue(this.fabric8.getReleaseAsync("async-app", NAMESPACE).get().isPresent());
		assertFalse(this.fabric8.listReleasesAsync(NAMESPACE).get().isEmpty());
		assertEquals(1, this.fabric8.getReleaseHistoryAsync("async-app", NAMESPACE).get().size());

		this.fabric8.applyAsync(NAMESPACE, yaml).get();
		this.fabric8.waitForReadyAsync(NAMESPACE, yaml, 30).get();
		assertEquals(1, this.fabric8.getResourceStatusesAsync(NAMESPACE, yaml).get().size());
		this.fabric8.deleteAsync(NAMESPACE, yaml).get();

		this.fabric8.deleteReleaseHistoryAsync("async-app", NAMESPACE).get();
		assertTrue(this.fabric8.getRelease("async-app", NAMESPACE).isEmpty());
	}

	@Test
	void getCapabilities_returnsClusterVersion() {
		assertNotNull(this.fabric8.getCapabilities());
		assertNotNull(this.fabric8.getCapabilities().kubeVersion());
		assertFalse(this.fabric8.getCapabilities().kubeVersion().isBlank());
	}

	private static String allWorkloadsManifest() {
		// Every workload kind checkResourceStatus switches on, so a single
		// getResourceStatuses
		// call exercises every readiness branch. Pods use the tiny pause image; nothing
		// is
		// waited on for readiness, so this stays fast.
		return """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: w-dep
				spec:
				  replicas: 1
				  selector:
				    matchLabels: {app: w-dep}
				  template:
				    metadata:
				      labels: {app: w-dep}
				    spec:
				      containers:
				        - {name: pause, image: registry.k8s.io/pause:3.9}
				---
				apiVersion: apps/v1
				kind: ReplicaSet
				metadata:
				  name: w-rs
				spec:
				  replicas: 1
				  selector:
				    matchLabels: {app: w-rs}
				  template:
				    metadata:
				      labels: {app: w-rs}
				    spec:
				      containers:
				        - {name: pause, image: registry.k8s.io/pause:3.9}
				---
				apiVersion: apps/v1
				kind: DaemonSet
				metadata:
				  name: w-ds
				spec:
				  selector:
				    matchLabels: {app: w-ds}
				  template:
				    metadata:
				      labels: {app: w-ds}
				    spec:
				      containers:
				        - {name: pause, image: registry.k8s.io/pause:3.9}
				---
				apiVersion: apps/v1
				kind: StatefulSet
				metadata:
				  name: w-ss
				spec:
				  serviceName: w-ss
				  replicas: 1
				  selector:
				    matchLabels: {app: w-ss}
				  template:
				    metadata:
				      labels: {app: w-ss}
				    spec:
				      containers:
				        - {name: pause, image: registry.k8s.io/pause:3.9}
				---
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: w-job
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				      containers:
				        - {name: done, image: registry.k8s.io/pause:3.9}
				---
				apiVersion: v1
				kind: Pod
				metadata:
				  name: w-pod
				spec:
				  containers:
				    - {name: pause, image: registry.k8s.io/pause:3.9}
				---
				apiVersion: v1
				kind: Service
				metadata:
				  name: w-svc
				spec:
				  selector: {app: w-dep}
				  ports:
				    - {port: 80}
				""";
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
