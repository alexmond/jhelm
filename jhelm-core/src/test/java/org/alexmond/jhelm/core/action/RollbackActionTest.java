package org.alexmond.jhelm.core.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
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
			.status("deployed")
			.build();

		Release.ReleaseInfo info2 = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(2))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status("deployed")
			.build();

		Release.ReleaseInfo info3 = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(2))
			.lastDeployed(OffsetDateTime.now())
			.status("deployed")
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

		rollbackAction.rollback("myapp", "default", 1);

		// apply receives the hook-stripped manifest (HookParser normalises docs with
		// trailing \n)
		verify(kubeService).apply("default", HookParser.stripHooks("---\nv1 manifest"));

		ArgumentCaptor<Release> releaseCaptor = ArgumentCaptor.forClass(Release.class);
		verify(kubeService).storeRelease(releaseCaptor.capture());

		Release storedRelease = releaseCaptor.getValue();
		assertEquals(4, storedRelease.getVersion());
		assertEquals("Rollback to 1", storedRelease.getInfo().getDescription());
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
			.status("deployed")
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

		rollbackAction.rollback("myapp", "default", 1);

		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		String strippedManifest = HookParser.stripHooks(manifest);

		verify(kubeService).apply("default", hooks.get(0).getYaml());
		verify(kubeService).apply("default", strippedManifest);
	}

	@Test
	void testRollbackFailsWhenRevisionNotFound() throws Exception {
		Release v1 = Release.builder().name("myapp").version(1).build();
		when(kubeService.getReleaseHistory(anyString(), anyString())).thenReturn(Arrays.asList(v1));

		ReleaseNotFoundException exception = assertThrows(ReleaseNotFoundException.class,
				() -> rollbackAction.rollback("myapp", "default", 99));

		assertTrue(exception.getMessage().contains("Revision 99 not found"));
	}

}
