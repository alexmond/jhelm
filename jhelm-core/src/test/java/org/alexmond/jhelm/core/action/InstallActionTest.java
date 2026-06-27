package org.alexmond.jhelm.core.action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.alexmond.jhelm.core.exception.DeploymentFailedException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.HookParser;

class InstallActionTest {

	@Mock
	private Engine engine;

	@Mock
	private KubeService kubeService;

	private InstallAction installAction;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		installAction = new InstallAction(engine, kubeService);
	}

	@Test
	void testInstallSuccess() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		String manifest = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: my-config\n";
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(manifest);
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.revision(1)
			.build());

		assertNotNull(release);
		assertEquals("my-release", release.getName());
		assertEquals("default", release.getNamespace());
		assertEquals(1, release.getVersion());
		assertEquals(ReleaseStatus.DEPLOYED, release.getInfo().getStatus());
		assertEquals(manifest, release.getManifest());

		verify(kubeService).apply("default", HookParser.stripHooks(manifest));
		verify(kubeService).storeRelease(any(Release.class));
	}

	@Test
	void testInstallPersistsUserValuesAsConfig() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("---\n");
		Map<String, Object> overrides = Map.of("replicaCount", 3, "image", Map.of("tag", "v2"));

		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.values(overrides)
			.revision(1)
			.dryRun(true)
			.build());

		assertNotNull(release.getConfig());
		assertEquals(3, release.getConfig().getValues().get("replicaCount"));
		assertEquals(Map.of("tag", "v2"), release.getConfig().getValues().get("image"));
	}

	@Test
	void testInstallDryRunSkipsKube() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("---\nkind: ConfigMap\n");

		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.revision(1)
			.dryRun(true)
			.build());

		assertEquals(ReleaseStatus.PENDING_INSTALL, release.getInfo().getStatus());
		verify(kubeService, never()).apply(anyString(), anyString());
		verify(kubeService, never()).storeRelease(any(Release.class));
	}

	@Test
	void testInstallRunsHooksAndStripsManifest() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		String hookYaml = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: my-release-pre-install
				  namespace: default
				  annotations:
				    helm.sh/hook: pre-install
				    helm.sh/hook-delete-policy: before-hook-creation,hook-succeeded
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		String regularYaml = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: my-config\n";
		String fullManifest = "---\n" + hookYaml + regularYaml;

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(fullManifest);
		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.revision(1)
			.build());

		List<HelmHook> hooks = HookParser.parseHooks(fullManifest);
		String strippedManifest = HookParser.stripHooks(fullManifest);

		// Hook apply called for pre-install hook
		verify(kubeService).apply("default", hooks.get(0).getYaml());
		// Regular apply called with stripped manifest
		verify(kubeService).apply("default", strippedManifest);
	}

	@Test
	void testInstallNoHooksSkipsHooks() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		String hookYaml = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: my-release-pre-install
				  namespace: default
				  annotations:
				    helm.sh/hook: pre-install
				    helm.sh/hook-delete-policy: before-hook-creation,hook-succeeded
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		String regularYaml = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: my-config\n";
		String fullManifest = "---\n" + hookYaml + regularYaml;

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(fullManifest);
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.revision(1)
			.noHooks(true)
			.build());

		List<HelmHook> hooks = HookParser.parseHooks(fullManifest);
		String strippedManifest = HookParser.stripHooks(fullManifest);

		// Hook resource is NOT applied when noHooks is true
		verify(kubeService, never()).apply("default", hooks.get(0).getYaml());
		// Regular manifest is still applied and the release stored
		verify(kubeService).apply("default", strippedManifest);
		verify(kubeService).storeRelease(any(Release.class));
	}

	@Test
	void testInstallWithNullKubeServiceNoop() throws Exception {
		InstallAction noKubeInstall = new InstallAction(engine, null);
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("---\nkind: ConfigMap\n");

		Release release = noKubeInstall.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.revision(1)
			.build());

		assertNotNull(release);
		assertEquals("my-release", release.getName());
	}

	@Test
	void testInstallRollsBackOnStoreFailure() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		String manifest = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: my-config\n";
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(manifest);
		doNothing().when(kubeService).apply(anyString(), anyString());
		doThrow(new RuntimeException("storage failed")).when(kubeService).storeRelease(any(Release.class));
		doNothing().when(kubeService).delete(anyString(), anyString());

		DeploymentFailedException ex = assertThrows(DeploymentFailedException.class,
				() -> installAction.install(InstallOptions.builder()
					.chart(chart)
					.releaseName("my-release")
					.namespace("default")
					.revision(1)
					.build()));

		assertEquals(HookParser.stripHooks(manifest), ex.getAppliedManifest());
		// Verify rollback was attempted
		verify(kubeService).delete("default", HookParser.stripHooks(manifest));
	}

	@Test
	void testInstallAppliesCrdsBeforeManifest() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		String crdYaml = "apiVersion: apiextensions.k8s.io/v1\nkind: CustomResourceDefinition\nmetadata:\n  name: foos.example.com\n";
		Chart chart = Chart.builder()
			.metadata(metadata)
			.values(new HashMap<>())
			.crds(List.of(Chart.Crd.builder().name("foos.yaml").data(crdYaml).build()))
			.build();

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("---\nkind: ConfigMap\n");
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.revision(1)
			.build());

		// CRD should be applied before the regular manifest
		org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(kubeService);
		inOrder.verify(kubeService).apply("default", crdYaml);
		inOrder.verify(kubeService).apply("default", "---\nkind: ConfigMap\n");
	}

	@Test
	void testInstallCreatesNamespaceBeforeApply() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		String manifest = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: my-config\n";
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(manifest);
		doNothing().when(kubeService).ensureNamespace(anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.revision(1)
			.createNamespace(true)
			.build());

		// Namespace is created before the manifest is applied.
		org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(kubeService);
		inOrder.verify(kubeService).ensureNamespace("default");
		inOrder.verify(kubeService).apply("default", HookParser.stripHooks(manifest));
	}

	@Test
	void testInstallSkipsNamespaceCreationByDefault() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		String manifest = "---\napiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: my-config\n";
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(manifest);
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("my-release")
			.namespace("default")
			.revision(1)
			.build());

		verify(kubeService, never()).ensureNamespace(anyString());
	}

	@Test
	void testInstallRejectsLibraryChart() {
		ChartMetadata metadata = ChartMetadata.builder().name("mylib").version("1.0.0").type("library").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		assertThrows(IllegalArgumentException.class,
				() -> installAction.install(InstallOptions.builder()
					.chart(chart)
					.releaseName("my-release")
					.namespace("default")
					.revision(1)
					.build()));
	}

}
