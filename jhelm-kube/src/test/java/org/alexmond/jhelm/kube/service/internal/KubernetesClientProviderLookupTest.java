package org.alexmond.jhelm.kube.service.internal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for #663: {@code lookup} must return the <em>whole</em> object (data,
 * stringData, spec, status, ...), not only metadata, so the near-universal "reuse the
 * existing auto-generated password" chart idiom keeps working across upgrades. These
 * exercise the exact serialization path {@code convertToMap} uses (the Kubernetes
 * client's Gson + {@link KubernetesClientProvider#jsonToJava}) without needing a live
 * API.
 */
class KubernetesClientProviderLookupTest {

	@SuppressWarnings("unchecked")
	private static Map<String, Object> lookupMap(Object model) {
		Object converted = KubernetesClientProvider.jsonToJava(JSON.getGson().toJsonTree(model));
		assertInstanceOf(Map.class, converted);
		return (Map<String, Object>) converted;
	}

	@Test
	void testSecretDataRoundTripsAsBase64() {
		// #663 core repro: a chart does `index $existing.data "db-password" | b64dec` —
		// the
		// looked-up Secret must expose .data as base64 strings, not drop it.
		V1Secret secret = new V1Secret().metadata(new V1ObjectMeta().name("app-db").namespace("prod"))
			.putDataItem("db-password", "s3cr3t-pw".getBytes(StandardCharsets.UTF_8));

		Map<String, Object> map = lookupMap(secret);

		assertNotNull(map.get("metadata"), "metadata must still be present");
		Object data = map.get("data");
		assertInstanceOf(Map.class, data, "#663: Secret .data must be present, not dropped");
		String b64 = (String) ((Map<String, Object>) data).get("db-password");
		assertNotNull(b64, "db-password entry must be present under .data");
		// Matches Helm: values are base64, so b64dec yields the original password.
		String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
		assertEquals("s3cr3t-pw", decoded, "the retained password must round-trip through b64dec");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testSecretStringDataPreserved() {
		V1Secret secret = new V1Secret().metadata(new V1ObjectMeta().name("s"))
			.putStringDataItem("token", "plain-value");

		Map<String, Object> map = lookupMap(secret);

		Object stringData = map.get("stringData");
		assertInstanceOf(Map.class, stringData, "#663: Secret .stringData must be present");
		assertEquals("plain-value", ((Map<String, Object>) stringData).get("token"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testConfigMapDataPreserved() {
		V1ConfigMap cm = new V1ConfigMap().metadata(new V1ObjectMeta().name("cfg")).putDataItem("key", "value");

		Map<String, Object> map = lookupMap(cm);

		Object data = map.get("data");
		assertInstanceOf(Map.class, data, "#663: ConfigMap .data must be present");
		assertEquals("value", ((Map<String, Object>) data).get("key"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testDeploymentSpecAndNumericFieldsPreserved() {
		V1Deployment dep = new V1Deployment().metadata(new V1ObjectMeta().name("web"))
			.spec(new V1DeploymentSpec().replicas(3));

		Map<String, Object> map = lookupMap(dep);

		Object spec = map.get("spec");
		assertInstanceOf(Map.class, spec, "#663: .spec must be present");
		// Integral numbers come back as Long so template arithmetic/comparison works.
		assertEquals(3L, ((Map<String, Object>) spec).get("replicas"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testListResultExposesItemsWithFullObjects() {
		V1SecretList list = new V1SecretList()
			.addItemsItem(new V1Secret().metadata(new V1ObjectMeta().name("a"))
				.putDataItem("k", "v1".getBytes(StandardCharsets.UTF_8)))
			.addItemsItem(new V1Secret().metadata(new V1ObjectMeta().name("b"))
				.putDataItem("k", "v2".getBytes(StandardCharsets.UTF_8)));

		Map<String, Object> map = lookupMap(list);

		Object items = map.get("items");
		assertInstanceOf(List.class, items, "a list lookup must expose .items");
		List<Object> itemList = (List<Object>) items;
		assertEquals(2, itemList.size());
		// each item is a full object, incl. .data — not metadata-only
		Map<String, Object> first = (Map<String, Object>) itemList.get(0);
		assertTrue(first.containsKey("data"), "#663: list items must retain .data too");
	}

	@Test
	void testNumberToJavaLongVsDouble() {
		assertEquals(3L, KubernetesClientProvider.numberToJava("3"));
		assertEquals(2.5d, KubernetesClientProvider.numberToJava("2.5"));
	}

}
