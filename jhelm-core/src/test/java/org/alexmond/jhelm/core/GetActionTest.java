package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class GetActionTest {

    @Mock
    private KubeService kubeService;

    private GetAction getAction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getAction = new GetAction(kubeService);
    }

    @Test
    void testGetRelease() throws Exception {
        Release release = createRelease();
        when(kubeService.getRelease("my-release", "default")).thenReturn(Optional.of(release));

        Optional<Release> result = getAction.getRelease("my-release", "default");

        assertTrue(result.isPresent());
        assertEquals("my-release", result.get().getName());
    }

    @Test
    void testGetReleaseNotFound() throws Exception {
        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        Optional<Release> result = getAction.getRelease("missing", "default");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetReleaseByRevision() throws Exception {
        Release v1 = createReleaseWithVersion(1);
        Release v2 = createReleaseWithVersion(2);
        when(kubeService.getReleaseHistory("my-release", "default")).thenReturn(List.of(v1, v2));

        Optional<Release> result = getAction.getReleaseByRevision("my-release", "default", 2);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getVersion());
    }

    @Test
    void testGetReleaseByRevisionNotFound() throws Exception {
        Release v1 = createReleaseWithVersion(1);
        when(kubeService.getReleaseHistory("my-release", "default")).thenReturn(List.of(v1));

        Optional<Release> result = getAction.getReleaseByRevision("my-release", "default", 99);

        assertFalse(result.isPresent());
    }

    @Test
    void testGetValuesUserOnly() throws Exception {
        Release release = createReleaseWithValues();

        String values = getAction.getValues(release, false);

        assertTrue(values.contains("key1: userVal1"));
    }

    @Test
    void testGetValuesAllMerged() throws Exception {
        Release release = createReleaseWithValues();

        String values = getAction.getValues(release, true);

        assertTrue(values.contains("key1: userVal1"));
        assertTrue(values.contains("chartDefault: defaultVal"));
    }

    @Test
    void testGetValuesEmptyConfig() throws Exception {
        Release release = createRelease();

        String values = getAction.getValues(release, false);

        assertEquals("{}", values);
    }

    @Test
    void testGetValuesAllWithNullConfig() throws Exception {
        Release release = Release.builder()
                .name("my-release")
                .chart(Chart.builder()
                        .metadata(ChartMetadata.builder().name("test").version("1.0.0").build())
                        .values(Map.of("chartKey", "chartVal"))
                        .build())
                .build();

        String values = getAction.getValues(release, true);

        assertTrue(values.contains("chartKey: chartVal"));
    }

    @Test
    void testGetManifest() {
        Release release = createRelease();

        String manifest = getAction.getManifest(release);

        assertEquals("---\nkind: Service\nmetadata:\n  name: my-svc\n", manifest);
    }

    @Test
    void testGetManifestNull() {
        Release release = Release.builder().name("empty").build();

        String manifest = getAction.getManifest(release);

        assertEquals("", manifest);
    }

    @Test
    void testGetNotes() {
        Release release = createReleaseWithNotes();

        String notes = getAction.getNotes(release);

        assertEquals("Thank you for installing test-chart.", notes);
    }

    @Test
    void testGetNotesEmpty() {
        Release release = createRelease();

        String notes = getAction.getNotes(release);

        assertEquals("", notes);
    }

    @Test
    void testGetNotesNullInfo() {
        Release release = Release.builder().name("no-info").build();

        String notes = getAction.getNotes(release);

        assertEquals("", notes);
    }

    @Test
    void testGetHooks() {
        String manifest = """
                ---
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: my-config
                ---
                apiVersion: batch/v1
                kind: Job
                metadata:
                  name: my-hook
                  annotations:
                    helm.sh/hook: pre-install
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: my-svc
                """;
        Release release = Release.builder().name("test").manifest(manifest).build();

        String hooks = getAction.getHooks(release);

        assertTrue(hooks.contains("helm.sh/hook: pre-install"));
        assertTrue(hooks.contains("kind: Job"));
        assertFalse(hooks.contains("kind: ConfigMap"));
        assertFalse(hooks.contains("kind: Service"));
    }

    @Test
    void testGetHooksNone() {
        Release release = Release.builder()
                .name("test")
                .manifest("---\nkind: Service\n")
                .build();

        String hooks = getAction.getHooks(release);

        assertEquals("", hooks);
    }

    @Test
    void testGetHooksEmptyManifest() {
        Release release = Release.builder().name("test").manifest("").build();

        assertEquals("", getAction.getHooks(release));
    }

    @Test
    void testGetHooksNullManifest() {
        Release release = Release.builder().name("test").build();

        assertEquals("", getAction.getHooks(release));
    }

    @Test
    void testGetMetadata() {
        Release release = createRelease();

        Map<String, Object> metadata = getAction.getMetadata(release);

        assertEquals("my-release", metadata.get("name"));
        assertEquals("default", metadata.get("namespace"));
        assertEquals(1, metadata.get("revision"));
        assertEquals("test-chart-1.0.0", metadata.get("chart"));
        assertEquals("deployed", metadata.get("status"));
    }

    @Test
    void testGetMetadataMinimal() {
        Release release = Release.builder()
                .name("minimal")
                .namespace("ns")
                .version(3)
                .build();

        Map<String, Object> metadata = getAction.getMetadata(release);

        assertEquals("minimal", metadata.get("name"));
        assertEquals("ns", metadata.get("namespace"));
        assertEquals(3, metadata.get("revision"));
        assertFalse(metadata.containsKey("chart"));
        assertFalse(metadata.containsKey("status"));
    }

    @Test
    void testGetAll() throws Exception {
        Release release = createReleaseWithNotes();

        String all = getAction.getAll(release, false);

        assertTrue(all.contains("MANIFEST:"));
        assertTrue(all.contains("NOTES:"));
        assertTrue(all.contains("VALUES:"));
    }

    @Test
    void testGetAllWithHooks() throws Exception {
        String manifest = """
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: my-svc
                ---
                apiVersion: batch/v1
                kind: Job
                metadata:
                  annotations:
                    helm.sh/hook: pre-install
                """;
        Release release = Release.builder()
                .name("test")
                .manifest(manifest)
                .info(Release.ReleaseInfo.builder().status("deployed").build())
                .build();

        String all = getAction.getAll(release, false);

        assertTrue(all.contains("MANIFEST:"));
        assertTrue(all.contains("HOOKS:"));
        assertTrue(all.contains("VALUES:"));
    }

    @Test
    void testToYaml() throws Exception {
        String yaml = getAction.toYaml(Map.of("key", "value"));
        assertTrue(yaml.contains("key: value"));
    }

    @Test
    void testToJson() throws Exception {
        String json = getAction.toJson(Map.of("key", "value"));
        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"value\""));
    }

    private Release createRelease() {
        return Release.builder()
                .name("my-release")
                .namespace("default")
                .version(1)
                .chart(Chart.builder()
                        .metadata(ChartMetadata.builder()
                                .name("test-chart")
                                .version("1.0.0")
                                .appVersion("1.0")
                                .build())
                        .build())
                .info(Release.ReleaseInfo.builder()
                        .status("deployed")
                        .lastDeployed(OffsetDateTime.now())
                        .build())
                .manifest("---\nkind: Service\nmetadata:\n  name: my-svc\n")
                .build();
    }

    private Release createReleaseWithVersion(int version) {
        return Release.builder()
                .name("my-release")
                .namespace("default")
                .version(version)
                .info(Release.ReleaseInfo.builder().status("deployed").build())
                .build();
    }

    private Release createReleaseWithValues() {
        return Release.builder()
                .name("my-release")
                .namespace("default")
                .version(1)
                .chart(Chart.builder()
                        .metadata(ChartMetadata.builder().name("test").version("1.0.0").build())
                        .values(Map.of("chartDefault", "defaultVal", "key1", "chartVal1"))
                        .build())
                .config(Release.MapConfig.builder()
                        .values(Map.of("key1", "userVal1"))
                        .build())
                .info(Release.ReleaseInfo.builder().status("deployed").build())
                .build();
    }

    private Release createReleaseWithNotes() {
        return Release.builder()
                .name("my-release")
                .namespace("default")
                .version(1)
                .chart(Chart.builder()
                        .metadata(ChartMetadata.builder().name("test-chart").version("1.0.0").build())
                        .build())
                .info(Release.ReleaseInfo.builder()
                        .status("deployed")
                        .notes("Thank you for installing test-chart.")
                        .build())
                .manifest("---\nkind: Service\n")
                .build();
    }
}
