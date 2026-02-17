package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

        Release upgradedRelease = upgradeAction.upgrade(currentRelease, newChart, null, false);

        assertNotNull(upgradedRelease);
        assertEquals("myapp", upgradedRelease.getName());
        assertEquals("default", upgradedRelease.getNamespace());
        assertEquals(2, upgradedRelease.getVersion());
        assertEquals("deployed", upgradedRelease.getInfo().getStatus());
        assertEquals("Upgrade complete", upgradedRelease.getInfo().getDescription());
        assertEquals(renderedManifest, upgradedRelease.getManifest());

        verify(kubeService).apply("default", renderedManifest);
        verify(kubeService).storeRelease(any(Release.class));

        ArgumentCaptor<Map<String, Object>> releaseDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(engine).render(eq(newChart), anyMap(), releaseDataCaptor.capture());

        Map<String, Object> releaseData = releaseDataCaptor.getValue();
        assertEquals("myapp", releaseData.get("Name"));
        assertEquals("default", releaseData.get("Namespace"));
        assertEquals(false, releaseData.get("IsInstall"));
        assertEquals(true, releaseData.get("IsUpgrade"));
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

        upgradeAction.upgrade(currentRelease, chart, overrideValues, false);

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

        Release upgradedRelease = upgradeAction.upgrade(currentRelease, chart, null, true);

        assertNotNull(upgradedRelease);
        assertEquals("pending-upgrade", upgradedRelease.getInfo().getStatus());
        assertEquals("Dry run complete", upgradedRelease.getInfo().getDescription());

        verify(kubeService, never()).apply(anyString(), anyString());
        verify(kubeService, never()).storeRelease(any(Release.class));
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

        Release upgradedRelease = upgradeAction.upgrade(currentRelease, chart, null, false);

        assertEquals(43, upgradedRelease.getVersion());
        assertEquals(info.getFirstDeployed(), upgradedRelease.getInfo().getFirstDeployed());
    }
}
