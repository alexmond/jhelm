package org.alexmond.jhelm.kube.service.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for #666: the apply path must carry <em>only</em> the fields the
 * source manifest declares. Round-tripping a bare Pod through the typed
 * {@code io.kubernetes.client} models materialized optional fields the manifest omits
 * (notably {@code spec.overhead} as an empty object), which the PodOverhead/RuntimeClass
 * admission controller then rejected — breaking {@code jhelm test} hook pods and
 * bare-{@code Pod} applies. The unstructured (Map-based) path fixes it.
 */
class HelmKubeServiceApplyTest {

	private static final String POD_YAML = """
			apiVersion: v1
			kind: Pod
			metadata:
			  name: test-connection
			  annotations:
			    helm.sh/hook: test
			spec:
			  restartPolicy: Never
			  containers:
			    - name: health
			      image: curlimages/curl:8.11.1
			      command: [sh, -c, "curl -sf http://svc:8080/login"]
			""";

	@SuppressWarnings("unchecked")
	private static Map<String, Object> firstDocument(String yaml) {
		Object doc = HelmKubeService.loadUnstructured(yaml).iterator().next();
		return assertInstanceOf(Map.class, doc);
	}

	@Test
	void testBarePodApplyBodyDoesNotInjectOverhead() {
		Map<String, Object> resource = firstDocument(POD_YAML);
		String applyBody = HelmKubeService.dumpUnstructured(resource);

		// #666: the source Pod declares no `overhead` (and no `runtimeClassName`), so the
		// applied body must not contain one — otherwise PodOverhead admission rejects it.
		assertFalse(applyBody.contains("overhead"), "apply body must not inject spec.overhead:\n" + applyBody);
		assertFalse(applyBody.contains("runtimeClassName"), "apply body must not inject runtimeClassName");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testUnstructuredRoundTripPreservesSourceFields() {
		Map<String, Object> resource = firstDocument(POD_YAML);

		assertEquals("v1", resource.get("apiVersion"));
		assertEquals("Pod", resource.get("kind"));
		Map<String, Object> spec = assertInstanceOf(Map.class, resource.get("spec"));
		// Only the keys the manifest declared — nothing materialized alongside them.
		assertEquals(java.util.Set.of("restartPolicy", "containers"), spec.keySet());

		// The re-serialized body still parses back to the same spec keys.
		Map<String, Object> reparsed = firstDocument(HelmKubeService.dumpUnstructured(resource));
		Map<String, Object> reparsedSpec = assertInstanceOf(Map.class, reparsed.get("spec"));
		assertEquals(spec.keySet(), reparsedSpec.keySet());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testCustomResourceParsesAsMapNotSkipped() {
		// A CRD/custom kind the typed client doesn't know deserializes as a Map, so the
		// unstructured path processes it. The old typed Yaml.loadAll + `instanceof
		// KubernetesObject` check silently skipped these, so apply installed them but
		// delete/status ignored them — a CRD that could be installed but not uninstalled.
		String crd = """
				apiVersion: cert-manager.io/v1
				kind: Certificate
				metadata:
				  name: my-cert
				spec:
				  secretName: my-cert-tls
				""";
		Map<String, Object> resource = firstDocument(crd);
		assertEquals("cert-manager.io/v1", resource.get("apiVersion"));
		assertEquals("Certificate", resource.get("kind"));
		Map<String, Object> metadata = assertInstanceOf(Map.class, resource.get("metadata"));
		assertEquals("my-cert", metadata.get("name"));
	}

	@Test
	void testMultiDocumentManifestParsesEachDocument() {
		String multi = POD_YAML + "---\n" + POD_YAML.replace("test-connection", "test-two");
		int count = 0;
		for (Object doc : HelmKubeService.loadUnstructured(multi)) {
			assertInstanceOf(Map.class, doc);
			count++;
		}
		assertEquals(2, count);
	}

	@Test
	void testSafeConstructorRejectsArbitraryTags() {
		// SafeConstructor must not instantiate arbitrary Java types from YAML tags.
		String malicious = "apiVersion: v1\nkind: Pod\nevil: !!java.net.URL [http://example.com]\n";
		boolean threw = false;
		try {
			HelmKubeService.loadUnstructured(malicious).iterator().next();
		}
		catch (RuntimeException ex) {
			threw = true;
		}
		assertTrue(threw, "SafeConstructor should refuse the !!java.net.URL tag");
	}

}
