package org.alexmond.jhelm.core.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.anyString;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.HookParser;

class UninstallActionTest {

	@Mock
	private KubeService kubeService;

	private UninstallAction uninstallAction;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		uninstallAction = new UninstallAction(kubeService);
	}

	@Test
	void testUninstallSuccess() throws Exception {
		Release release = Release.builder().name("myapp").namespace("default").manifest("---\nkind: Service\n").build();

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));
		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

		uninstallAction.uninstall("myapp", "default");

		verify(kubeService).delete("default", "---\nkind: Service\n");
		verify(kubeService).deleteReleaseHistory("myapp", "default");
	}

	@Test
	void testUninstallRunsHooksAndStripsManifest() throws Exception {
		String hookYaml = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: myapp-pre-delete
				  namespace: default
				  annotations:
				    helm.sh/hook: pre-delete
				    helm.sh/hook-delete-policy: before-hook-creation
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		String regularYaml = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: myapp-cfg\n";
		String fullManifest = "---\n" + hookYaml + regularYaml;

		Release release = Release.builder().name("myapp").namespace("default").manifest(fullManifest).build();

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));
		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());
		doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

		uninstallAction.uninstall("myapp", "default");

		List<HelmHook> hooks = HookParser.parseHooks(fullManifest);
		String strippedManifest = HookParser.stripHooks(fullManifest);

		// Pre-delete hook: delete (before-hook-creation) + apply + waitForReady
		verify(kubeService).apply("default", hooks.get(0).getYaml());
		// Regular resource deletion uses stripped manifest
		verify(kubeService).delete("default", strippedManifest);
		verify(kubeService).deleteReleaseHistory("myapp", "default");
	}

	@Test
	void testUninstallSkipsResourcesWithKeepPolicy() throws Exception {
		String keepYaml = """
				apiVersion: v1
				kind: PersistentVolumeClaim
				metadata:
				  name: myapp-data
				  annotations:
				    helm.sh/resource-policy: keep
				spec:
				  accessModes: [ReadWriteOnce]
				""";
		String regularYaml = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: myapp-cfg\n";
		String fullManifest = "---\n" + keepYaml + regularYaml;

		Release release = Release.builder().name("myapp").namespace("default").manifest(fullManifest).build();

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));
		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

		uninstallAction.uninstall("myapp", "default");

		// Only ConfigMap should be deleted — PVC with resource-policy: keep is preserved
		String deletedManifest = HookParser.stripKeptResources(HookParser.stripHooks(fullManifest));
		verify(kubeService).delete("default", deletedManifest);
	}

	@Test
	void testUninstallThrowsWhenReleaseNotFound() throws Exception {
		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

		ReleaseNotFoundException exception = assertThrows(ReleaseNotFoundException.class,
				() -> uninstallAction.uninstall("non-existent", "default"));

		assertTrue(exception.getMessage().contains("Release not found"));
	}

}
