package org.alexmond.jhelm.core.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import org.alexmond.jhelm.core.exception.DeploymentFailedException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.HookParser;

class UpgradeActionTest {

	@Mock
	private Engine engine;

	@Mock
	private KubeService kubeService;

	private UpgradeAction upgradeAction;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		upgradeAction = new UpgradeAction(engine, kubeService);
	}

	@Test
	void testUpgradeSuccess() throws Exception {
		ChartMetadata oldMetadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart oldChart = Chart.builder().metadata(oldMetadata).values(new HashMap<>()).build();

		Release.ReleaseInfo oldInfo = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status("deployed")
			.description("Install complete")
			.build();

		Release currentRelease = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(oldChart)
			.info(oldInfo)
			.build();

		ChartMetadata newMetadata = ChartMetadata.builder().name("mychart").version("2.0.0").build();
		Map<String, Object> newValues = new HashMap<>();
		newValues.put("replicaCount", 3);
		Chart newChart = Chart.builder().metadata(newMetadata).values(newValues).build();

		String renderedManifest = "---\napiVersion: v1\nkind: Service";
		when(engine.render(eq(newChart), anyMap(), anyMap())).thenReturn(renderedManifest);
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		Release upgradedRelease = upgradeAction.upgrade(currentRelease, newChart, null, UpgradeValueStrategy.DEFAULT,
				false);

		assertNotNull(upgradedRelease);
		assertEquals("myapp", upgradedRelease.getName());
		assertEquals("default", upgradedRelease.getNamespace());
		assertEquals(2, upgradedRelease.getVersion());
		assertEquals("deployed", upgradedRelease.getInfo().getStatus());
		assertEquals("Upgrade complete", upgradedRelease.getInfo().getDescription());
		assertEquals(renderedManifest, upgradedRelease.getManifest());

		// apply receives the hook-stripped manifest (HookParser normalises docs with
		// trailing \n)
		verify(kubeService).apply("default", HookParser.stripHooks(renderedManifest));
		verify(kubeService).storeRelease(any(Release.class));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> releaseDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(engine).render(eq(newChart), anyMap(), releaseDataCaptor.capture());

		Map<String, Object> releaseData = releaseDataCaptor.getValue();
		assertEquals("myapp", releaseData.get("Name"));
		assertEquals("default", releaseData.get("Namespace"));
		assertEquals(false, releaseData.get("IsInstall"));
		assertEquals(true, releaseData.get("IsUpgrade"));
		assertEquals(2, releaseData.get("Revision"));
	}

	@Test
	void testUpgradeWithOverrideValues() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Map<String, Object> chartValues = new HashMap<>();
		chartValues.put("replicaCount", 1);
		chartValues.put("image", "nginx:1.0");
		Chart chart = Chart.builder().metadata(metadata).values(chartValues).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status("deployed")
			.build();

		Release currentRelease = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.info(info)
			.build();

		Map<String, Object> overrideValues = new HashMap<>();
		overrideValues.put("replicaCount", 5);

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("manifest");

		upgradeAction.upgrade(currentRelease, chart, overrideValues, UpgradeValueStrategy.DEFAULT, false);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> valuesCaptor = ArgumentCaptor.forClass(Map.class);
		verify(engine).render(eq(chart), valuesCaptor.capture(), anyMap());

		Map<String, Object> mergedValues = valuesCaptor.getValue();
		assertEquals(5, mergedValues.get("replicaCount"));
		assertEquals("nginx:1.0", mergedValues.get("image"));
	}

	@Test
	void testUpgradeDryRun() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status("deployed")
			.build();

		Release currentRelease = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.info(info)
			.build();

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("dry-run-manifest");

		Release upgradedRelease = upgradeAction.upgrade(currentRelease, chart, null, UpgradeValueStrategy.DEFAULT,
				true);

		assertNotNull(upgradedRelease);
		assertEquals("pending-upgrade", upgradedRelease.getInfo().getStatus());
		assertEquals("Dry run complete", upgradedRelease.getInfo().getDescription());

		verify(kubeService, never()).apply(anyString(), anyString());
		verify(kubeService, never()).storeRelease(any(Release.class));
	}

	@Test
	void testUpgradeRunsHooksAndStripsManifest() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("2.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status("deployed")
			.build();

		Release currentRelease = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.info(info)
			.build();

		String hookYaml = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: myapp-pre-upgrade
				  namespace: default
				  annotations:
				    helm.sh/hook: pre-upgrade
				    helm.sh/hook-delete-policy: before-hook-creation
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		String regularYaml = "---\napiVersion: v1\nkind: Service\nmetadata:\n  name: myapp-svc\n";
		String fullManifest = "---\n" + hookYaml + regularYaml;

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(fullManifest);
		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());
		doNothing().when(kubeService).storeRelease(any(Release.class));

		upgradeAction.upgrade(currentRelease, chart, null, UpgradeValueStrategy.DEFAULT, false);

		List<HelmHook> hooks = HookParser.parseHooks(fullManifest);
		String strippedManifest = HookParser.stripHooks(fullManifest);

		// Hook apply called with hook yaml, regular apply called with stripped manifest
		verify(kubeService).apply("default", hooks.get(0).getYaml());
		verify(kubeService).apply("default", strippedManifest);
	}

	@Test
	void testUpgradeIncrementsVersion() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(5))
			.lastDeployed(OffsetDateTime.now().minusDays(2))
			.status("deployed")
			.build();

		Release currentRelease = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(42)
			.chart(chart)
			.info(info)
			.build();

		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("manifest");

		Release upgradedRelease = upgradeAction.upgrade(currentRelease, chart, null, UpgradeValueStrategy.DEFAULT,
				false);

		assertEquals(43, upgradedRelease.getVersion());
		assertEquals(info.getFirstDeployed(), upgradedRelease.getInfo().getFirstDeployed());
	}

	@Test
	void testUpgradeReappliesPreviousOnStoreFailure() throws Exception {
		ChartMetadata metadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		String previousManifest = "---\napiVersion: v1\nkind: Service\nmetadata:\n  name: old-svc\n";
		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status("deployed")
			.build();

		Release currentRelease = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.manifest(previousManifest)
			.info(info)
			.build();

		String newManifest = "---\napiVersion: v1\nkind: Service\nmetadata:\n  name: new-svc\n";
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(newManifest);
		doNothing().when(kubeService).apply(anyString(), anyString());
		doThrow(new RuntimeException("storage failed")).when(kubeService).storeRelease(any(Release.class));

		assertThrows(DeploymentFailedException.class,
				() -> upgradeAction.upgrade(currentRelease, chart, null, UpgradeValueStrategy.DEFAULT, false));

		// Verify: new manifest applied, then previous manifest re-applied on rollback
		verify(kubeService, times(2)).apply(eq("default"), anyString());
	}

	@Test
	void testUpgradeRejectsLibraryChart() {
		ChartMetadata metadata = ChartMetadata.builder().name("mylib").version("1.0.0").type("library").build();
		Chart chart = Chart.builder().metadata(metadata).values(new HashMap<>()).build();

		Release currentRelease = Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(chart)
			.info(Release.ReleaseInfo.builder().status("deployed").build())
			.build();

		assertThrows(IllegalArgumentException.class,
				() -> upgradeAction.upgrade(currentRelease, chart, null, UpgradeValueStrategy.DEFAULT, false));
	}

	// --- Value-resolution strategy tests ---------------------------------------------
	// currentRelease carries prior user values (config) and an OLD chart whose default
	// "defaultKey" is "old"; the newChart changes that default to "new".

	private Release currentReleaseWithPrior() {
		ChartMetadata oldMetadata = ChartMetadata.builder().name("mychart").version("1.0.0").build();
		Map<String, Object> oldDefaults = new HashMap<>();
		oldDefaults.put("defaultKey", "old");
		oldDefaults.put("replicaCount", 1);
		Chart oldChart = Chart.builder().metadata(oldMetadata).values(oldDefaults).build();

		Map<String, Object> prior = new HashMap<>();
		prior.put("userKey", "prior");

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now().minusDays(1))
			.lastDeployed(OffsetDateTime.now().minusDays(1))
			.status("deployed")
			.build();

		return Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.chart(oldChart)
			.config(Release.MapConfig.builder().values(prior).build())
			.info(info)
			.build();
	}

	private Chart newChartWithChangedDefault() {
		ChartMetadata newMetadata = ChartMetadata.builder().name("mychart").version("2.0.0").build();
		Map<String, Object> newDefaults = new HashMap<>();
		newDefaults.put("defaultKey", "new");
		newDefaults.put("replicaCount", 1);
		return Chart.builder().metadata(newMetadata).values(newDefaults).build();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> renderValuesFor(Release upgraded) {
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(engine).render(any(Chart.class), captor.capture(), anyMap());
		return captor.getValue();
	}

	@Test
	void testDefaultNoOverridesReusesPriorConfig() {
		Release current = currentReleaseWithPrior();
		Chart newChart = newChartWithChangedDefault();
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("manifest");

		Release upgraded = upgradeAction.upgrade(current, newChart, null, UpgradeValueStrategy.DEFAULT, true);

		Map<String, Object> render = renderValuesFor(upgraded);
		// Prior user value reused; new chart default present.
		assertEquals("prior", render.get("userKey"));
		assertEquals("new", render.get("defaultKey"));
		// Config persists the prior user values.
		assertEquals("prior", upgraded.getConfig().getValues().get("userKey"));
	}

	@Test
	void testDefaultWithOverridesDropsPrior() {
		Release current = currentReleaseWithPrior();
		Chart newChart = newChartWithChangedDefault();
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("extra", "x");
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("manifest");

		Release upgraded = upgradeAction.upgrade(current, newChart, overrides, UpgradeValueStrategy.DEFAULT, true);

		Map<String, Object> render = renderValuesFor(upgraded);
		// Overrides used; prior user value dropped for unspecified keys.
		assertEquals("x", render.get("extra"));
		assertEquals(null, render.get("userKey"));
		assertEquals("new", render.get("defaultKey"));
		// Config holds only the overrides.
		assertEquals("x", upgraded.getConfig().getValues().get("extra"));
		assertEquals(null, upgraded.getConfig().getValues().get("userKey"));
	}

	@Test
	void testResetOverridesNewDefaultsIgnoringPrior() {
		Release current = currentReleaseWithPrior();
		Chart newChart = newChartWithChangedDefault();
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("extra", "x");
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("manifest");

		Release upgraded = upgradeAction.upgrade(current, newChart, overrides, UpgradeValueStrategy.RESET, true);

		Map<String, Object> render = renderValuesFor(upgraded);
		// Overrides over NEW defaults; prior ignored.
		assertEquals("x", render.get("extra"));
		assertEquals(null, render.get("userKey"));
		assertEquals("new", render.get("defaultKey"));
		assertEquals(null, upgraded.getConfig().getValues().get("userKey"));
	}

	@Test
	void testReuseRendersAgainstOldDefaults() {
		Release current = currentReleaseWithPrior();
		Chart newChart = newChartWithChangedDefault();
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("extra", "x");
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("manifest");

		Release upgraded = upgradeAction.upgrade(current, newChart, overrides, UpgradeValueStrategy.REUSE, true);

		Map<String, Object> render = renderValuesFor(upgraded);
		// Prior + overrides; render based on OLD defaults — the changed new default does
		// NOT appear (defaultKey stays "old").
		assertEquals("prior", render.get("userKey"));
		assertEquals("x", render.get("extra"));
		assertEquals("old", render.get("defaultKey"));
		// Config is the prior + override user layer.
		assertEquals("prior", upgraded.getConfig().getValues().get("userKey"));
		assertEquals("x", upgraded.getConfig().getValues().get("extra"));
	}

	@Test
	void testResetThenReuseRendersAgainstNewDefaults() {
		Release current = currentReleaseWithPrior();
		Chart newChart = newChartWithChangedDefault();
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("extra", "x");
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn("manifest");

		Release upgraded = upgradeAction.upgrade(current, newChart, overrides, UpgradeValueStrategy.RESET_THEN_REUSE,
				true);

		Map<String, Object> render = renderValuesFor(upgraded);
		// Prior + overrides over NEW defaults — the changed new default DOES appear
		// (defaultKey becomes "new").
		assertEquals("prior", render.get("userKey"));
		assertEquals("x", render.get("extra"));
		assertEquals("new", render.get("defaultKey"));
		// Config is the same prior + override user layer as REUSE.
		assertEquals("prior", upgraded.getConfig().getValues().get("userKey"));
		assertEquals("x", upgraded.getConfig().getValues().get("extra"));
	}

}
