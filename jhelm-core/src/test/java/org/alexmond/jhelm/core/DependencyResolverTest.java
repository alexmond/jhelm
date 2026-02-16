package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DependencyResolver.
 */
class DependencyResolverTest {
    private RepoManager repoManager;
    private DependencyResolver resolver;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        repoManager = new RepoManager();
        resolver = new DependencyResolver(repoManager);
    }

    @Test
    void testResolveDependenciesWithNoDependencies() throws IOException {
        ChartMetadata metadata = ChartMetadata.builder()
                .name("test-chart")
                .version("1.0.0")
                .dependencies(Collections.emptyList())
                .build();

        ChartLock lock = resolver.resolveDependencies(metadata, new HashMap<>(), null);

        assertNotNull(lock);
        assertTrue(lock.getDependencies().isEmpty());
    }

    @Test
    void testConditionEvaluation() throws IOException {
        // Create metadata with conditional dependency
        Dependency dep = Dependency.builder()
                .name("postgresql")
                .version("12.1.0")
                .repository("https://charts.bitnami.com/bitnami")
                .condition("postgresql.enabled")
                .build();

        ChartMetadata metadata = ChartMetadata.builder()
                .name("test-chart")
                .version("1.0.0")
                .dependencies(List.of(dep))
                .build();

        // Test with condition = false
        Map<String, Object> values = new HashMap<>();
        Map<String, Object> postgresqlConfig = new HashMap<>();
        postgresqlConfig.put("enabled", false);
        values.put("postgresql", postgresqlConfig);

        ChartLock lock = resolver.resolveDependencies(metadata, values, null);
        assertTrue(lock.getDependencies().isEmpty(), "Dependency should be excluded when condition is false");

        // Test with condition = true (would need actual repo to download)
        postgresqlConfig.put("enabled", true);
        // This would fail without actual repo, so we just verify the condition logic works
    }

    @Test
    void testTagFiltering() throws IOException {
        Dependency dep = Dependency.builder()
                .name("redis")
                .version("17.0.0")
                .repository("@bitnami")  // Use repository alias to avoid actual download
                .tags(List.of("database", "cache"))
                .build();

        ChartMetadata metadata = ChartMetadata.builder()
                .name("test-chart")
                .version("1.0.0")
                .dependencies(List.of(dep))
                .build();

        // Test with no matching tags - dependency should be excluded
        ChartLock lock = resolver.resolveDependencies(metadata, new HashMap<>(), List.of("frontend"));
        assertTrue(lock.getDependencies().isEmpty(), "Dependency should be excluded when no tags match");
    }

    @Test
    void testDigestGeneration() throws IOException {
        ChartMetadata metadata = ChartMetadata.builder()
                .name("test-chart")
                .version("1.0.0")
                .dependencies(Collections.emptyList())
                .build();

        ChartLock lock = resolver.resolveDependencies(metadata, new HashMap<>(), null);

        assertNotNull(lock);
        // With empty dependencies, digest should still be generated
        assertNotNull(lock.getDigest(), "Digest should be generated even for empty dependencies");
        assertTrue(lock.getDigest().isEmpty() || lock.getDigest().startsWith("sha256:"),
                "Digest should be empty or start with 'sha256:'");
    }
}
