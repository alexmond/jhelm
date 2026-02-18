package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShowActionTest {

    @Mock
    private ChartLoader chartLoader;

    private ShowAction showAction;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        showAction = new ShowAction(chartLoader);
    }

    @Test
    void testShowChart() throws Exception {
        ChartMetadata metadata = ChartMetadata.builder()
                .name("mychart")
                .version("1.0.0")
                .description("A test chart")
                .build();

        Chart chart = Chart.builder()
                .metadata(metadata)
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        String result = showAction.showChart("/path/to/chart");

        assertNotNull(result);
        assertTrue(result.contains("name: mychart"));
        assertTrue(result.contains("version: 1.0.0"));
        assertTrue(result.contains("description: A test chart"));
    }

    @Test
    void testShowValuesWithData() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("replicaCount", 3);
        values.put("image", "nginx:latest");

        Chart chart = Chart.builder()
                .metadata(ChartMetadata.builder().name("mychart").build())
                .values(values)
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        String result = showAction.showValues("/path/to/chart");

        assertNotNull(result);
        assertTrue(result.contains("replicaCount"));
        assertTrue(result.contains("image"));
    }

    @ParameterizedTest
    @MethodSource("emptyValuesTestCases")
    void testShowValuesEmptyOrNull(Map<String, Object> values) throws Exception {
        Chart chart = Chart.builder()
                .metadata(ChartMetadata.builder().name("mychart").build())
                .values(values)
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        assertEquals("{}", showAction.showValues("/path/to/chart"));
    }

    static Stream<Arguments> emptyValuesTestCases() {
        return Stream.of(
            Arguments.of(new HashMap<>()),
            Arguments.of((Map<String, Object>) null)
        );
    }

    @Test
    void testShowReadmeWithData() throws Exception {
        String readme = "# My Chart\n\nThis is a test chart.";

        Chart chart = Chart.builder()
                .metadata(ChartMetadata.builder().name("mychart").build())
                .readme(readme)
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        String result = showAction.showReadme("/path/to/chart");

        assertEquals(readme, result);
    }

    @Test
    void testShowReadmeEmpty() throws Exception {
        Chart chart = Chart.builder()
                .metadata(ChartMetadata.builder().name("mychart").build())
                .readme(null)
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        String result = showAction.showReadme("/path/to/chart");

        assertEquals("", result);
    }

    @Test
    void testShowCrdsWithData() throws Exception {
        Chart.Crd crd1 = new Chart.Crd("crd1.yaml", "apiVersion: v1\nkind: CustomResourceDefinition");
        Chart.Crd crd2 = new Chart.Crd("crd2.yaml", "apiVersion: v1\nkind: AnotherCRD");

        Chart chart = Chart.builder()
                .metadata(ChartMetadata.builder().name("mychart").build())
                .crds(Arrays.asList(crd1, crd2))
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        String result = showAction.showCrds("/path/to/chart");

        assertNotNull(result);
        assertTrue(result.contains("CustomResourceDefinition"));
        assertTrue(result.contains("AnotherCRD"));
        assertTrue(result.contains("---"));
    }

    @ParameterizedTest
    @MethodSource("emptyCrdsTestCases")
    void testShowCrdsEmptyOrNull(List<Chart.Crd> crds) throws Exception {
        Chart chart = Chart.builder()
                .metadata(ChartMetadata.builder().name("mychart").build())
                .crds(crds)
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        assertEquals("", showAction.showCrds("/path/to/chart"));
    }

    static Stream<Arguments> emptyCrdsTestCases() {
        return Stream.of(
            Arguments.of(new ArrayList<>()),
            Arguments.of((List<Chart.Crd>) null)
        );
    }

    @Test
    void testShowAll() throws Exception {
        ChartMetadata metadata = ChartMetadata.builder()
                .name("mychart")
                .version("1.0.0")
                .build();

        Map<String, Object> values = new HashMap<>();
        values.put("replicaCount", 3);

        String readme = "# My Chart";

        Chart.Crd crd = new Chart.Crd("crd.yaml", "apiVersion: v1\nkind: CRD");

        Chart chart = Chart.builder()
                .metadata(metadata)
                .values(values)
                .readme(readme)
                .crds(Arrays.asList(crd))
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        String result = showAction.showAll("/path/to/chart");

        assertNotNull(result);
        assertTrue(result.contains("# Chart.yaml"));
        assertTrue(result.contains("name: mychart"));
        assertTrue(result.contains("# values.yaml"));
        assertTrue(result.contains("replicaCount"));
        assertTrue(result.contains("# README.md"));
        assertTrue(result.contains("# My Chart"));
        assertTrue(result.contains("# CRDs"));
        assertTrue(result.contains("kind: CRD"));
    }

    @Test
    void testShowAllMinimal() throws Exception {
        ChartMetadata metadata = ChartMetadata.builder()
                .name("mychart")
                .version("1.0.0")
                .build();

        Chart chart = Chart.builder()
                .metadata(metadata)
                .values(new HashMap<>())
                .readme(null)
                .crds(null)
                .build();

        when(chartLoader.load(any(File.class))).thenReturn(chart);

        String result = showAction.showAll("/path/to/chart");

        assertNotNull(result);
        assertTrue(result.contains("# Chart.yaml"));
        assertTrue(result.contains("# values.yaml"));
        assertTrue(result.contains("{}"));
        assertFalse(result.contains("# README.md"));
        assertFalse(result.contains("# CRDs"));
    }
}
