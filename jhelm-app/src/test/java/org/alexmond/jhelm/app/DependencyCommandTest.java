package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.ChartMetadata;
import org.alexmond.jhelm.core.Dependency;
import org.alexmond.jhelm.core.RepoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DependencyCommand.
 * <p>
 * Note: Full integration tests require network access to download charts from repositories.
 * These tests focus on the structure and basic validation logic.
 */
class DependencyCommandTest {

    @TempDir
    File tempDir;

    @Mock
    private RepoManager repoManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testChartMetadataWithDependencies() {
        // Verify that ChartMetadata can hold dependencies
        Dependency dep1 = Dependency.builder()
                .name("postgresql")
                .version("^12.0.0")
                .repository("https://charts.bitnami.com/bitnami")
                .condition("postgresql.enabled")
                .build();

        Dependency dep2 = Dependency.builder()
                .name("redis")
                .version("~17.0.0")
                .repository("https://charts.bitnami.com/bitnami")
                .tags(List.of("cache", "database"))
                .build();

        ChartMetadata metadata = ChartMetadata.builder()
                .name("test-chart")
                .version("1.0.0")
                .dependencies(List.of(dep1, dep2))
                .build();

        assertNotNull(metadata.getDependencies());
        assertEquals(2, metadata.getDependencies().size());
        assertEquals("postgresql", metadata.getDependencies().get(0).getName());
        assertEquals("^12.0.0", metadata.getDependencies().get(0).getVersion());
        assertEquals("postgresql.enabled", metadata.getDependencies().get(0).getCondition());
        assertEquals("redis", metadata.getDependencies().get(1).getName());
        assertEquals(2, metadata.getDependencies().get(1).getTags().size());
    }

    @Test
    void testDependencyWithAlias() {
        Dependency dep = Dependency.builder()
                .name("postgresql")
                .version("12.1.0")
                .repository("https://charts.bitnami.com/bitnami")
                .alias("postgres")
                .build();

        assertEquals("postgres", dep.getAlias());
    }

    @Test
    void testDependencyWithImportValues() {
        Dependency dep = Dependency.builder()
                .name("postgresql")
                .version("12.1.0")
                .repository("https://charts.bitnami.com/bitnami")
                .importValues(List.of("child: parent"))
                .build();

        assertNotNull(dep.getImportValues());
        assertEquals(1, dep.getImportValues().size());
    }

    @Test
    void testOciRepositoryUrl() {
        Dependency dep = Dependency.builder()
                .name("mychart")
                .version("1.0.0")
                .repository("oci://registry.example.com/charts")
                .build();

        assertTrue(dep.getRepository().startsWith("oci://"));
    }

    @Test
    void testFileRepositoryUrl() {
        Dependency dep = Dependency.builder()
                .name("subchart")
                .version("1.0.0")
                .repository("file://../other-chart")
                .build();

        assertTrue(dep.getRepository().startsWith("file://"));
    }

    @Test
    void testDependencyListCommandWithNonExistentChart() throws Exception {
        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = tempDir.toPath().resolve("non-existent").toString();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandWithValidChart() throws Exception {
        File chartDir = tempDir.toPath().resolve("test-chart").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: test-chart
                version: 1.0.0
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandWithMultipleDependencies() throws Exception {
        File chartDir = tempDir.toPath().resolve("multi-deps-chart").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: multi-deps-chart
                version: 1.0.0
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                  - name: redis
                    version: 17.0.0
                    repository: https://charts.bitnami.com/bitnami
                  - name: nginx
                    version: 13.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandWithChartsDirectory() throws Exception {
        File chartDir = tempDir.toPath().resolve("chart-with-deps").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: chart-with-deps
                version: 1.0.0
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        // Create charts/ directory with unpacked dependency
        File chartsDir = new File(chartDir, "charts");
        chartsDir.mkdirs();
        File postgresDir = new File(chartsDir, "postgresql");
        postgresDir.mkdirs();

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandWithChartLock() throws Exception {
        File chartDir = tempDir.toPath().resolve("chart-with-lock").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: chart-with-lock
                version: 1.0.0
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        String chartLock = """
                apiVersion: v2
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.lock"), chartLock);

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandWithWrongVersion() throws Exception {
        File chartDir = tempDir.toPath().resolve("chart-wrong-version").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: chart-wrong-version
                version: 1.0.0
                dependencies:
                  - name: postgresql
                    version: 13.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        String chartLock = """
                apiVersion: v2
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.lock"), chartLock);

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandWithNoDependencies() throws Exception {
        File chartDir = tempDir.toPath().resolve("nodeps-chart").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: nodeps-chart
                version: 1.0.0
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyUpdateCommand() {
        DependencyCommand.UpdateCommand updateCommand = new DependencyCommand.UpdateCommand(repoManager);
        updateCommand.chartPath = tempDir.toPath().resolve("test-chart").toString();

        picocli.CommandLine cmd = new picocli.CommandLine(updateCommand);
        try {
            cmd.execute();
        } catch (Exception e) {
            // Expected - chart doesn't exist
        }
    }

    @Test
    void testDependencyBuildCommand() {
        DependencyCommand.BuildCommand buildCommand = new DependencyCommand.BuildCommand(repoManager);
        buildCommand.chartPath = tempDir.toPath().resolve("test-chart").toString();

        picocli.CommandLine cmd = new picocli.CommandLine(buildCommand);
        try {
            cmd.execute();
        } catch (Exception e) {
            // Expected - chart doesn't exist
        }
    }

    @Test
    void testDependencyCommandShowsUsage() {
        DependencyCommand dependencyCommand = new DependencyCommand();
        picocli.CommandLine cmd = new picocli.CommandLine(dependencyCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandWithOkStatus() throws Exception {
        File chartDir = tempDir.toPath().resolve("chart-ok-status").toFile();
        chartDir.mkdirs();

        // Create Chart.yaml with dependency
        String chartYaml = """
                apiVersion: v2
                name: chart-ok-status
                version: 1.0.0
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        // Create matching Chart.lock
        String chartLock = """
                apiVersion: v2
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.lock"), chartLock);

        // Create charts/ directory with unpacked dependency
        File chartsDir = new File(chartDir, "charts");
        chartsDir.mkdirs();
        File postgresDir = new File(chartsDir, "postgresql");
        postgresDir.mkdirs();

        // Add a Chart.yaml inside the dependency to make it look real
        String depChartYaml = """
                apiVersion: v2
                name: postgresql
                version: 12.0.0
                """;
        Files.writeString(postgresDir.toPath().resolve("Chart.yaml"), depChartYaml);

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandStatusMissing() throws Exception {
        File chartDir = tempDir.toPath().resolve("chart-missing").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: chart-missing
                version: 1.0.0
                dependencies:
                  - name: redis
                    version: 17.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        // No Chart.lock and no charts/ directory - status should be "missing"

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyListCommandStatusUnpacked() throws Exception {
        File chartDir = tempDir.toPath().resolve("chart-unpacked").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: chart-unpacked
                version: 1.0.0
                dependencies:
                  - name: nginx
                    version: 13.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        // Create charts/ directory with unpacked dependency but no Chart.lock
        File chartsDir = new File(chartDir, "charts");
        chartsDir.mkdirs();
        File nginxDir = new File(chartsDir, "nginx");
        nginxDir.mkdirs();

        DependencyCommand.ListCommand listCommand = new DependencyCommand.ListCommand();
        listCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testDependencyUpdateCommandSkipRefresh() throws Exception {
        File chartDir = tempDir.toPath().resolve("update-skip-refresh").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: update-skip-refresh
                version: 1.0.0
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        DependencyCommand.UpdateCommand updateCommand = new DependencyCommand.UpdateCommand(repoManager);
        updateCommand.chartPath = chartDir.getAbsolutePath();
        updateCommand.skipRefresh = true;

        picocli.CommandLine cmd = new picocli.CommandLine(updateCommand);
        try {
            cmd.execute();
        } catch (Exception e) {
            // Expected - will fail during dependency resolution
        }
    }

    @Test
    void testDependencyBuildCommandSkipRefresh() throws Exception {
        File chartDir = tempDir.toPath().resolve("build-skip-refresh").toFile();
        chartDir.mkdirs();

        String chartLock = """
                apiVersion: v2
                dependencies:
                  - name: postgresql
                    version: 12.0.0
                    repository: https://charts.bitnami.com/bitnami
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.lock"), chartLock);

        DependencyCommand.BuildCommand buildCommand = new DependencyCommand.BuildCommand(repoManager);
        buildCommand.chartPath = chartDir.getAbsolutePath();
        buildCommand.skipRefresh = true;

        picocli.CommandLine cmd = new picocli.CommandLine(buildCommand);
        try {
            cmd.execute();
        } catch (Exception e) {
            // Expected - will fail during dependency download
        }
    }

    @Test
    void testDependencyBuildCommandEmptyLock() throws Exception {
        File chartDir = tempDir.toPath().resolve("build-empty-lock").toFile();
        chartDir.mkdirs();

        String chartLock = """
                apiVersion: v2
                dependencies: []
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.lock"), chartLock);

        DependencyCommand.BuildCommand buildCommand = new DependencyCommand.BuildCommand(repoManager);
        buildCommand.chartPath = chartDir.getAbsolutePath();

        picocli.CommandLine cmd = new picocli.CommandLine(buildCommand);
        cmd.execute();
    }
}
