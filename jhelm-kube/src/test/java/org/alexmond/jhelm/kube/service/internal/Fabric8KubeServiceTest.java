package org.alexmond.jhelm.kube.service.internal;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Fabric8 backend's error translation, verifying a list failure keeps
 * the cluster's HTTP status code and surfaces the cluster reason on the thrown message
 * (issue #797). The list query itself is exercised by the cluster-gated integration test;
 * here only the {@link KubernetesClientException} catch/enrich path is asserted. The
 * fluent Secrets DSL returns self-types, so one mock stands in for the whole
 * {@code secrets().inNamespace(..).withLabels(..)} chain, with {@code list()} stubbed to
 * fail.
 */
class Fabric8KubeServiceTest {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private MixedOperation<Secret, SecretList, Resource<Secret>> failingSecrets(RuntimeException failure) {
		MixedOperation secrets = mock(MixedOperation.class);
		when(secrets.inNamespace(anyString())).thenReturn(secrets);
		when(secrets.inAnyNamespace()).thenReturn(secrets);
		when(secrets.withLabels(anyMap())).thenReturn(secrets);
		when(secrets.withLabel(anyString(), anyString())).thenReturn(secrets);
		when(secrets.list()).thenThrow(failure);
		return secrets;
	}

	@Test
	void listReleasesFailureKeepsStatusCodeAndReason() {
		MixedOperation<Secret, SecretList, Resource<Secret>> secrets = failingSecrets(
				new KubernetesClientException("Forbidden: cannot list secrets in namespace default", 403, null));
		KubernetesClient client = mock(KubernetesClient.class);
		when(client.secrets()).thenReturn(secrets);
		Fabric8KubeService service = new Fabric8KubeService(client);

		KubernetesOperationException ex = assertThrows(KubernetesOperationException.class,
				() -> service.listReleases("default"));

		assertEquals(403, ex.getStatusCode());
		assertTrue(ex.getMessage().contains("list releases"), ex.getMessage());
		assertTrue(ex.getMessage().contains("Forbidden"), ex.getMessage());
	}

	@Test
	void listAllReleasesFailureKeepsStatusCodeAndReason() {
		MixedOperation<Secret, SecretList, Resource<Secret>> secrets = failingSecrets(
				new KubernetesClientException("Unauthorized: token expired", 401, null));
		KubernetesClient client = mock(KubernetesClient.class);
		when(client.secrets()).thenReturn(secrets);
		Fabric8KubeService service = new Fabric8KubeService(client);

		KubernetesOperationException ex = assertThrows(KubernetesOperationException.class, service::listAllReleases);

		assertEquals(401, ex.getStatusCode());
		assertTrue(ex.getMessage().contains("list all releases"), ex.getMessage());
		assertTrue(ex.getMessage().contains("Unauthorized"), ex.getMessage());
	}

}
