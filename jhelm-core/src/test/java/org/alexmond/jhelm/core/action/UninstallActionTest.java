package org.alexmond.jhelm.core.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.anyString;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.service.CascadePolicy;
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
		doNothing().when(kubeService).delete(anyString(), anyString(), any());
		doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

		uninstallAction.uninstall(UninstallOptions.builder().releaseName("myapp").namespace("default").build());

		verify(kubeService).delete("default", "---\nkind: Service\n", CascadePolicy.BACKGROUND);
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
		doNothing().when(kubeService).delete(anyString(), anyString(), any());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());
		doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

		uninstallAction.uninstall(UninstallOptions.builder().releaseName("myapp").namespace("default").build());

		List<HelmHook> hooks = HookParser.parseHooks(fullManifest);
		String strippedManifest = HookParser.stripHooks(fullManifest);

		// Pre-delete hook: delete (before-hook-creation) + apply + waitForReady
		verify(kubeService).apply("default", hooks.get(0).getYaml());
		// Regular resource deletion uses stripped manifest
		verify(kubeService).delete("default", strippedManifest, CascadePolicy.BACKGROUND);
		verify(kubeService).deleteReleaseHistory("myapp", "default");
	}

	@Test
	void testUninstallNoHooksSkipsHooks() throws Exception {
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
		doNothing().when(kubeService).delete(anyString(), anyString(), any());
		doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

		uninstallAction
			.uninstall(UninstallOptions.builder().releaseName("myapp").namespace("default").noHooks(true).build());

		List<HelmHook> hooks = HookParser.parseHooks(fullManifest);
		String deletableManifest = HookParser.stripKeptResources(HookParser.stripHooks(fullManifest));

		// Hook resource is NOT applied when noHooks is true
		verify(kubeService, never()).apply("default", hooks.get(0).getYaml());
		// Regular resources are still deleted and history removed
		verify(kubeService).delete("default", deletableManifest, CascadePolicy.BACKGROUND);
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
		doNothing().when(kubeService).delete(anyString(), anyString(), any());
		doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

		uninstallAction.uninstall(UninstallOptions.builder().releaseName("myapp").namespace("default").build());

		// Only ConfigMap should be deleted — PVC with resource-policy: keep is preserved
		String deletedManifest = HookParser.stripKeptResources(HookParser.stripHooks(fullManifest));
		verify(kubeService).delete("default", deletedManifest, CascadePolicy.BACKGROUND);
	}

	@Test
	void testUninstallKeepHistoryStoresUninstalledReleaseAndKeepsHistory() throws Exception {
		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status(ReleaseStatus.DEPLOYED)
			.build();
		Release release = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.manifest("---\nkind: Service\n")
			.info(info)
			.build();

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));
		doNothing().when(kubeService).delete(anyString(), anyString(), any());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		uninstallAction
			.uninstall(UninstallOptions.builder().releaseName("myapp").namespace("default").keepHistory(true).build());

		ArgumentCaptor<Release> releaseCaptor = ArgumentCaptor.forClass(Release.class);
		verify(kubeService).storeRelease(releaseCaptor.capture());
		assertEquals(ReleaseStatus.UNINSTALLED, releaseCaptor.getValue().getInfo().getStatus());
		assertEquals("Uninstallation complete", releaseCaptor.getValue().getInfo().getDescription());
		verify(kubeService, never()).deleteReleaseHistory(anyString(), anyString());
	}

	@Test
	void testUninstallWithoutKeepHistoryDeletesHistoryAndDoesNotStore() throws Exception {
		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status(ReleaseStatus.DEPLOYED)
			.build();
		Release release = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.manifest("---\nkind: Service\n")
			.info(info)
			.build();

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));
		doNothing().when(kubeService).delete(anyString(), anyString(), any());
		doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

		uninstallAction.uninstall(UninstallOptions.builder().releaseName("myapp").namespace("default").build());

		verify(kubeService).deleteReleaseHistory("myapp", "default");
		verify(kubeService, never()).storeRelease(any(Release.class));
	}

	@Test
	void testDryRunDeletesNothingAndLeavesHistory() {
		Release release = Release.builder().name("myapp").namespace("default").manifest("---\nkind: Service\n").build();
		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));

		uninstallAction
			.uninstall(UninstallOptions.builder().releaseName("myapp").namespace("default").dryRun(true).build());

		verify(kubeService, never()).delete(anyString(), anyString(), any());
		verify(kubeService, never()).deleteReleaseHistory(anyString(), anyString());
		verify(kubeService, never()).storeRelease(any(Release.class));
	}

	@Test
	void testWaitCallsWaitForDeleted() {
		Release release = Release.builder().name("myapp").namespace("default").manifest("---\nkind: Service\n").build();
		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));

		uninstallAction.uninstall(
				UninstallOptions.builder().releaseName("myapp").namespace("default").wait(true).timeout(42).build());

		verify(kubeService).waitForDeleted(eq("default"), anyString(), eq(42));
	}

	@Test
	void testCascadePolicyIsPassedToDelete() {
		Release release = Release.builder().name("myapp").namespace("default").manifest("---\nkind: Service\n").build();
		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));

		uninstallAction.uninstall(UninstallOptions.builder()
			.releaseName("myapp")
			.namespace("default")
			.cascade(CascadePolicy.FOREGROUND)
			.build());

		verify(kubeService).delete(eq("default"), anyString(), eq(CascadePolicy.FOREGROUND));
	}

	@Test
	void testCustomDescriptionStoredWhenKeepingHistory() {
		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status(ReleaseStatus.DEPLOYED)
			.build();
		Release release = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.manifest("---\nkind: Service\n")
			.info(info)
			.build();
		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));

		uninstallAction.uninstall(UninstallOptions.builder()
			.releaseName("myapp")
			.namespace("default")
			.keepHistory(true)
			.description("scaling down")
			.build());

		ArgumentCaptor<Release> releaseCaptor = ArgumentCaptor.forClass(Release.class);
		verify(kubeService).storeRelease(releaseCaptor.capture());
		assertEquals("scaling down", releaseCaptor.getValue().getInfo().getDescription());
	}

	@Test
	void testUninstallThrowsWhenReleaseNotFound() throws Exception {
		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

		ReleaseNotFoundException exception = assertThrows(ReleaseNotFoundException.class, () -> uninstallAction
			.uninstall(UninstallOptions.builder().releaseName("non-existent").namespace("default").build()));

		assertTrue(exception.getMessage().contains("Release not found"));
	}

}
