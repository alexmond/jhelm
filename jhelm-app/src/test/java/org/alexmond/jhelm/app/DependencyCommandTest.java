package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.ChartMetadata;
import org.alexmond.jhelm.core.Dependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
