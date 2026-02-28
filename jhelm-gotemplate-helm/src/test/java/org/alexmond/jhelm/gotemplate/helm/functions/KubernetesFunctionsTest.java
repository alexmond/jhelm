package org.alexmond.jhelm.gotemplate.helm.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.junit.jupiter.api.Test;

class KubernetesFunctionsTest {

	@Test
	void testGetFunctionsContainsExpected() {
		Map<String, Function> fns = KubernetesFunctions.getFunctions();
		assertTrue(fns.containsKey("lookup"));
		assertTrue(fns.containsKey("kubeVersion"));
		assertEquals(2, fns.size());
	}

	@Test
	void testLookupWithoutProviderReturnsEmptyMap() {
		Function lookup = KubernetesFunctions.getFunctions().get("lookup");
		Object result = lookup.invoke(new Object[] { "v1", "Pod", "default", "mypod" });
		assertInstanceOf(Map.class, result);
		assertTrue(((Map<?, ?>) result).isEmpty());
	}

	@Test
	void testKubeVersionWithoutProviderReturnsStub() {
		Function kubeVersion = KubernetesFunctions.getFunctions().get("kubeVersion");
		Object result = kubeVersion.invoke(new Object[] {});
		assertInstanceOf(Map.class, result);
		@SuppressWarnings("unchecked")
		Map<String, Object> version = (Map<String, Object>) result;
		assertEquals("1", version.get("Major"));
		assertEquals("28", version.get("Minor"));
		assertEquals("v1.28.0", version.get("GitVersion"));
	}

	@Test
	void testLookupWithProviderDelegates() {
		KubernetesProvider provider = new KubernetesProvider() {
			@Override
			public boolean isAvailable() {
				return true;
			}

			@Override
			public Map<String, Object> lookup(String apiVersion, String kind, String namespace, String name) {
				return Map.of("metadata", Map.of("name", name));
			}

			@Override
			public Map<String, Object> getVersion() {
				return Map.of("Major", "1", "Minor", "29");
			}
		};

		Function lookup = KubernetesFunctions.getFunctions(provider).get("lookup");
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) lookup
			.invoke(new Object[] { "v1", "Pod", "default", "mypod" });
		@SuppressWarnings("unchecked")
		Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
		assertEquals("mypod", metadata.get("name"));
	}

	@Test
	void testLookupWithProviderInsufficientArgs() {
		KubernetesProvider provider = new KubernetesProvider() {
			@Override
			public boolean isAvailable() {
				return true;
			}

			@Override
			public Map<String, Object> lookup(String apiVersion, String kind, String namespace, String name) {
				return Map.of();
			}

			@Override
			public Map<String, Object> getVersion() {
				return Map.of();
			}
		};

		Function lookup = KubernetesFunctions.getFunctions(provider).get("lookup");
		assertThrows(RuntimeException.class, () -> lookup.invoke(new Object[] { "v1", "Pod" }));
	}

	@Test
	void testKubeVersionWithProviderDelegates() {
		KubernetesProvider provider = new KubernetesProvider() {
			@Override
			public boolean isAvailable() {
				return true;
			}

			@Override
			public Map<String, Object> lookup(String apiVersion, String kind, String namespace, String name) {
				return Map.of();
			}

			@Override
			public Map<String, Object> getVersion() {
				return Map.of("Major", "1", "Minor", "30", "GitVersion", "v1.30.0");
			}
		};

		Function kubeVersion = KubernetesFunctions.getFunctions(provider).get("kubeVersion");
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) kubeVersion.invoke(new Object[] {});
		assertEquals("1", result.get("Major"));
		assertEquals("30", result.get("Minor"));
		assertEquals("v1.30.0", result.get("GitVersion"));
	}

	@Test
	void testLookupWithUnavailableProviderReturnsEmpty() {
		KubernetesProvider provider = new KubernetesProvider() {
			@Override
			public boolean isAvailable() {
				return false;
			}

			@Override
			public Map<String, Object> lookup(String apiVersion, String kind, String namespace, String name) {
				throw new RuntimeException("should not be called");
			}

			@Override
			public Map<String, Object> getVersion() {
				throw new RuntimeException("should not be called");
			}
		};

		Function lookup = KubernetesFunctions.getFunctions(provider).get("lookup");
		Object result = lookup.invoke(new Object[] { "v1", "Pod", "default", "test" });
		assertInstanceOf(Map.class, result);
		assertTrue(((Map<?, ?>) result).isEmpty());
	}

}
