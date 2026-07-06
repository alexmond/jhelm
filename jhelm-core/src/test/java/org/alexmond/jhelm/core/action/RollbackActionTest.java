package org.alexmond.jhelm.core.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.eq;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.HookParser;

class RollbackActionTest {

	@Mock
	private KubeService kubeService;

	private RollbackAction rollbackAction;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		rollbackAction = new RollbackAction(kubeService);
	}

	@Test
	void testRollbackSuccess() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).build();

		Release.ReleaseInfo info1 = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(2))
			.lastDeployed(OffsetDateTime.now().minusDays(2))
			.status(ReleaseStatus.DEPLOYED)
			.build();

		Release.ReleaseInfo info2 = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(2))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status(ReleaseStatus.DEPLOYED)
			.build();

		Release.ReleaseInfo info3 = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(2))
			.lastDeployed(OffsetDateTime.now())
			.status(ReleaseStatus.DEPLOYED)
			.build();

		Release v1 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.manifest("---\nv1 manifest")
			.info(info1)
			.build();

		Release v2 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(2)
			.chart(chart)
			.manifest("---\nv2 manifest")
			.info(info2)
			.build();

		Release v3 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(3)
			.chart(chart)
			.manifest("---\nv3 manifest")
			.info(info3)
			.build();

		List<Release> history = Arrays.asList(v3, v2, v1);
		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(history);
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		rollbackAction
			.rollback(RollbackOptions.builder().releaseName("myapp").namespace("default").revision(1).build());

		// apply receives the hook-stripped manifest (HookParser normalises docs with
		// trailing \n)
		verify(kubeService).apply("default", HookParser.stripHooks("---\nv1 manifest"));

		ArgumentCaptor<Release> releaseCaptor = ArgumentCaptor.forClass(Release.class);
		verify(kubeService).storeRelease(releaseCaptor.capture());

		Release storedRelease = releaseCaptor.getValue();
		assertEquals(4, storedRelease.getVersion());
		assertEquals("Rollback to 1", storedRelease.getInfo().getDescription());

		// The 3-arg overload defaults to Helm's --history-max default of 10.
		verify(kubeService).pruneReleaseHistory("myapp", "default", 10);
	}

	@Test
	void testRollbackPrunesHistoryAfterStoreWithGivenMaxHistory() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status(ReleaseStatus.DEPLOYED)
			.build();

		Release v1 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.manifest("---\nv1 manifest")
			.info(info)
			.build();

		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(Arrays.asList(v1));
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		rollbackAction.rollback(
				RollbackOptions.builder().releaseName("myapp").namespace("default").revision(1).maxHistory(5).build());

		// Prune is invoked with the passed maxHistory, after the release is stored.
		InOrder order = inOrder(kubeService);
		order.verify(kubeService).storeRelease(any(Release.class));
		order.verify(kubeService).pruneReleaseHistory("myapp", "default", 5);
	}

	@Test
	void testRollbackRunsHooksAndStripsManifest() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).build();

		String hookYaml = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: myapp-pre-rollback
				  namespace: default
				  annotations:
				    helm.sh/hook: pre-rollback
				    helm.sh/hook-delete-policy: before-hook-creation
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		String regularYaml = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: myapp-cfg\n";
		String manifest = "---\n" + hookYaml + regularYaml;

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status(ReleaseStatus.DEPLOYED)
			.build();

		Release v1 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.manifest(manifest)
			.info(info)
			.build();

		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(Arrays.asList(v1));
		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		rollbackAction
			.rollback(RollbackOptions.builder().releaseName("myapp").namespace("default").revision(1).build());

		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		String strippedManifest = HookParser.stripHooks(manifest);

		verify(kubeService).apply("default", hooks.get(0).getYaml());
		verify(kubeService).apply("default", strippedManifest);
	}

	@Test
	void testRollbackNoHooksSkipsHooks() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).build();

		String hookYaml = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: myapp-pre-rollback
				  namespace: default
				  annotations:
				    helm.sh/hook: pre-rollback
				    helm.sh/hook-delete-policy: before-hook-creation
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		String regularYaml = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: myapp-cfg\n";
		String manifest = "---\n" + hookYaml + regularYaml;

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status(ReleaseStatus.DEPLOYED)
			.build();

		Release v1 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.manifest(manifest)
			.info(info)
			.build();

		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(Arrays.asList(v1));
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		rollbackAction.rollback(
				RollbackOptions.builder().releaseName("myapp").namespace("default").revision(1).noHooks(true).build());

		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		String strippedManifest = HookParser.stripHooks(manifest);

		// Hook resource is NOT applied when noHooks is true
		verify(kubeService, never()).apply("default", hooks.get(0).getYaml());
		// Regular manifest is still applied and the release stored
		verify(kubeService).apply("default", strippedManifest);
		verify(kubeService).storeRelease(any(Release.class));
	}

	@Test
	void testRollbackReturnsNewRelease() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).build();

		Release.ReleaseInfo info1 = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(2))
			.lastDeployed(OffsetDateTime.now().minusDays(2))
			.status(ReleaseStatus.DEPLOYED)
			.build();

		Release.ReleaseInfo info2 = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(2))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status(ReleaseStatus.DEPLOYED)
			.build();

		Release v1 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.manifest("---\nv1 manifest")
			.info(info1)
			.build();

		Release v2 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(2)
			.chart(chart)
			.manifest("---\nv2 manifest")
			.info(info2)
			.build();

		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(Arrays.asList(v2, v1));
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		Release result = rollbackAction
			.rollback(RollbackOptions.builder().releaseName("myapp").namespace("default").revision(1).build());

		assertNotNull(result);
		assertEquals(3, result.getVersion());
		assertEquals(ReleaseStatus.DEPLOYED, result.getInfo().getStatus());
	}

	@Test
	void testRollbackFailsWhenRevisionNotFound() throws Exception {
		Release v1 = Release.builder().name("myapp").version(1).build();
		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(Arrays.asList(v1));

		ReleaseNotFoundException exception = assertThrows(ReleaseNotFoundException.class, () -> rollbackAction
			.rollback(RollbackOptions.builder().releaseName("myapp").namespace("default").revision(99).build()));

		assertTrue(exception.getMessage().contains("Revision 99 not found"));
	}

	// --- #683 rollback semantics ---

	private List<Release> twoRevisionHistory() {
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder().name("mychart").version("1.0.0").build())
			.build();
		Release v1 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.manifest("---\nv1 manifest")
			.info(Release.ReleaseInfo.builder()
				.firstDeployed(OffsetDateTime.now().minusDays(2))
				.lastDeployed(OffsetDateTime.now().minusDays(2))
				.status(ReleaseStatus.DEPLOYED)
				.build())
			.build();
		Release v2 = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(2)
			.chart(chart)
			.manifest("---\nv2 manifest")
			.info(Release.ReleaseInfo.builder()
				.firstDeployed(OffsetDateTime.now().minusDays(2))
				.lastDeployed(OffsetDateTime.now().minusDays(1))
				.status(ReleaseStatus.DEPLOYED)
				.build())
			.build();
		return Arrays.asList(v2, v1);
	}

	@Test
	void testDryRunDoesNotApplyOrStore() {
		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(twoRevisionHistory());

		rollbackAction.rollback(
				RollbackOptions.builder().releaseName("myapp").namespace("default").revision(1).dryRun(true).build());

		verify(kubeService, never()).apply(anyString(), anyString());
		verify(kubeService, never()).storeRelease(any(Release.class));
	}

	@Test
	void testForceDeletesBeforeApply() {
		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(twoRevisionHistory());
		doNothing().when(kubeService).apply(anyString(), anyString());

		rollbackAction.rollback(
				RollbackOptions.builder().releaseName("myapp").namespace("default").revision(1).force(true).build());

		String manifest = HookParser.stripHooks("---\nv1 manifest");
		InOrder ord = inOrder(kubeService);
		ord.verify(kubeService).delete("default", manifest);
		ord.verify(kubeService).apply("default", manifest);
	}

	@Test
	void testCleanupOnFailDeletesAndRethrows() {
		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(twoRevisionHistory());
		doThrow(new RuntimeException("apply failed")).when(kubeService).apply(anyString(), anyString());

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> rollbackAction.rollback(RollbackOptions.builder()
					.releaseName("myapp")
					.namespace("default")
					.revision(1)
					.cleanupOnFail(true)
					.build()));

		assertTrue(ex.getMessage().contains("apply failed"));
		verify(kubeService).delete("default", HookParser.stripHooks("---\nv1 manifest"));
		verify(kubeService, never()).storeRelease(any(Release.class));
	}

	@Test
	void testRecreatePodsRestartsWorkloads() {
		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(twoRevisionHistory());
		doNothing().when(kubeService).apply(anyString(), anyString());

		rollbackAction.rollback(RollbackOptions.builder()
			.releaseName("myapp")
			.namespace("default")
			.revision(1)
			.recreatePods(true)
			.build());

		verify(kubeService).restartWorkloads("default", HookParser.stripHooks("---\nv1 manifest"));
	}

	@Test
	void testWaitCallsWaitForReadyWithJobsFlag() {
		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(twoRevisionHistory());
		doNothing().when(kubeService).apply(anyString(), anyString());

		rollbackAction.rollback(RollbackOptions.builder()
			.releaseName("myapp")
			.namespace("default")
			.revision(1)
			.wait(true)
			.waitForJobs(true)
			.timeout(77)
			.build());

		verify(kubeService).waitForReady(eq("default"), anyString(), eq(77), eq(true));
	}

}
