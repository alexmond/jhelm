package org.alexmond.jhelm.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestDiffTest {

	private static final String DEPLOYMENT_FOO = """
			apiVersion: apps/v1
			kind: Deployment
			metadata:
			  name: foo
			""";

	private static final String SERVICE_FOO = """
			apiVersion: v1
			kind: Service
			metadata:
			  name: foo
			""";

	private static final String CONFIGMAP_BAR = """
			apiVersion: v1
			kind: ConfigMap
			metadata:
			  name: bar
			""";

	private static String manifest(String... docs) {
		StringBuilder sb = new StringBuilder();
		for (String doc : docs) {
			sb.append("---\n").append(doc.trim()).append('\n');
		}
		return sb.toString();
	}

	@Test
	void testDroppedResourceIsOrphan() {
		String oldManifest = manifest(DEPLOYMENT_FOO, SERVICE_FOO);
		String newManifest = manifest(DEPLOYMENT_FOO, CONFIGMAP_BAR);

		String orphans = ManifestDiff.orphanedResources(oldManifest, newManifest);

		assertTrue(orphans.contains("kind: Service"), "orphan output should contain the dropped Service");
		assertTrue(orphans.contains("name: foo"), "orphan output should contain the Service name");
		assertFalse(orphans.contains("kind: Deployment"), "retained Deployment must not be an orphan");
	}

	@Test
	void testIdenticalManifestsHaveNoOrphans() {
		String oldManifest = manifest(DEPLOYMENT_FOO, SERVICE_FOO);

		String orphans = ManifestDiff.orphanedResources(oldManifest, oldManifest);

		assertTrue(orphans.isEmpty(), "identical manifests should yield no orphans");
	}

	@Test
	void testNewSupersetHasNoOrphans() {
		String oldManifest = manifest(DEPLOYMENT_FOO);
		String newManifest = manifest(DEPLOYMENT_FOO, SERVICE_FOO, CONFIGMAP_BAR);

		String orphans = ManifestDiff.orphanedResources(oldManifest, newManifest);

		assertTrue(orphans.isEmpty(), "a superset new manifest should yield no orphans");
	}

	@Test
	void testChangedFieldsSameIdentityIsNotOrphan() {
		String oldDeployment = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: foo
				spec:
				  replicas: 1
				""";
		String newDeployment = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: foo
				spec:
				  replicas: 3
				""";

		String orphans = ManifestDiff.orphanedResources(manifest(oldDeployment), manifest(newDeployment));

		assertTrue(orphans.isEmpty(), "same identity with changed fields is not an orphan");
	}

	@Test
	void testNamespaceIsPartOfIdentity() {
		String oldService = """
				apiVersion: v1
				kind: Service
				metadata:
				  name: foo
				  namespace: alpha
				""";
		String newService = """
				apiVersion: v1
				kind: Service
				metadata:
				  name: foo
				  namespace: beta
				""";

		String orphans = ManifestDiff.orphanedResources(manifest(oldService), manifest(newService));

		assertTrue(orphans.contains("namespace: alpha"), "same kind/name in a different namespace should be an orphan");
	}

	@Test
	void testEmptyOldManifestYieldsNoOrphans() {
		String orphans = ManifestDiff.orphanedResources("", manifest(SERVICE_FOO));
		assertTrue(orphans.isEmpty(), "blank old manifest yields no orphans");
	}

}
