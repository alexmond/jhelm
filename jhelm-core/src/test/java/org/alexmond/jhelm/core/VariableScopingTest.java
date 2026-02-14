package org.alexmond.jhelm.core;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test variable scoping and template processing with a dedicated test chart.
 * This test ensures that:
 * - Variables can be assigned and accessed correctly
 * - Variables work inside range loops
 * - Nested range loops maintain correct scoping
 * - If conditions work with maps, lists, and assigned variables
 * - Or/And/Not conditions work correctly
 * - Subcharts have proper value isolation and global value access
 */
class VariableScopingTest {

    private final ChartLoader chartLoader = new ChartLoader();
    private final Engine engine = new Engine();
    private final InstallAction installAction = new InstallAction(engine, null);

    @Test
    void testVariableScopingChart() throws Exception {
        // Load the test chart
        File chartDir = new File("src/test/resources/test-charts/variable-scoping-test");
        Chart chart = chartLoader.load(chartDir);
        assertNotNull(chart, "Chart should be loaded");

        // Render the chart
        Release release = installAction.install(chart, "test-release", "default", Map.of(), 1, true);
        assertNotNull(release, "Release should be created");

        String manifest = release.getManifest();
        assertNotNull(manifest, "Manifest should not be null");
        assertFalse(manifest.trim().isEmpty(), "Manifest should not be empty");

        // Debug: print manifest to see what was rendered
        System.out.println("=== Rendered Manifest ===");
        System.out.println(manifest);
        System.out.println("=== End Manifest ===");

        // Test 1: Root variable assignment
        assertTrue(manifest.contains("Root variable assigned: YES"),
                "Root variable should be assigned successfully");
        assertTrue(manifest.contains("Can access Release: YES"),
                "Should be able to access Release from root variable");

        // Test 2: Variable in range loop
        assertTrue(manifest.contains("Can access root in range: YES"),
                "Should be able to access root variable inside range loop");
        assertTrue(manifest.contains("Root Release Name: test-release"),
                "Should be able to access Release.Name from root variable in range");

        // Test 3: Nested range loops
        assertTrue(manifest.contains("Can access root: YES"),
                "Should be able to access root variable in nested range loops");

        // Test 4: If condition with map
        assertTrue(manifest.contains("Map items exists and is truthy"),
                "If condition with map should evaluate to true");

        // Test 5: If condition with assigned variable
        assertTrue(manifest.contains("Assigned map variable is truthy"),
                "If condition with assigned map variable should evaluate to true");

        // Test 6: If condition with list
        assertTrue(manifest.contains("Server list is truthy"),
                "If condition with list should evaluate to true");

        // Test 7: Or condition
        assertTrue(manifest.contains("OR with maps works"),
                "OR condition with maps should work correctly");

        // Test 8: And condition
        assertTrue(manifest.contains("AND with map and bool works"),
                "AND condition should work correctly");

        // Test 9: Not empty condition
        assertTrue(manifest.contains("Empty map is empty (correct)"),
                "Not empty condition should correctly identify empty map");

        // Test 10: Complex nested variable access
        assertTrue(manifest.contains("Server server1 is enabled") ||
                manifest.contains("Server server3 is enabled"),
                "Should be able to filter servers by enabled flag");
        assertTrue(manifest.contains("Features available from root context"),
                "Should be able to access root context features in nested range");

        // Test 11: Variable in with block
        assertTrue(manifest.contains("Inside with block"),
                "With block should execute");
        assertTrue(manifest.contains("Root Release Name: test-release"),
                "Should be able to access root variable inside with block");

        // Test 12: Range with index
        assertTrue(manifest.contains("0: server1"),
                "Range should provide correct index");
        assertTrue(manifest.contains("Global domain from root: example.com"),
                "Should be able to access global values from root variable");

        // Subchart tests
        assertTrue(manifest.contains("name: test-release-subchart"),
                "Subchart ConfigMap should be created");
        assertTrue(manifest.contains("Replicas: 3"),
                "Subchart should use overridden values from parent");
        assertTrue(manifest.contains("Global domain: example.com"),
                "Subchart should have access to global values");
        assertTrue(manifest.contains("Parent serverList is not accessible"),
                "Subchart should not have access to parent-specific values");

        System.out.println("All variable scoping tests passed!");
        System.out.println("\nRendered manifest length: " + manifest.length() + " bytes");
    }

    @Test
    void testEmptyMapAndListHandling() throws Exception {
        File chartDir = new File("src/test/resources/test-charts/variable-scoping-test");
        Chart chart = chartLoader.load(chartDir);

        Release release = installAction.install(chart, "test-release", "default", Map.of(), 1, true);
        String manifest = release.getManifest();

        // Test that empty map evaluates to false
        assertTrue(manifest.contains("Empty map is empty (correct)"),
                "Empty map should be detected correctly");

        // Ensure no errors occur when processing empty values
        assertFalse(manifest.contains("NullPointerException"),
                "Should not have null pointer exceptions");
        assertFalse(manifest.contains("error"),
                "Should not have template errors");
    }

    @Test
    void testSubchartValueIsolation() throws Exception {
        File chartDir = new File("src/test/resources/test-charts/variable-scoping-test");
        Chart chart = chartLoader.load(chartDir);

        // Override subchart values
        Map<String, Object> overrides = Map.of(
                "subchart", Map.of(
                        "replicas", 5,
                        "config", Map.of(
                                "setting1", "overridden1"
                        )
                )
        );

        Release release = installAction.install(chart, "test-release", "default", overrides, 1, true);
        String manifest = release.getManifest();

        // Verify overrides are applied
        assertTrue(manifest.contains("Replicas: 5"),
                "Subchart should use overridden replicas value");
        assertTrue(manifest.contains("Config setting1: overridden1"),
                "Subchart should use overridden config values");
    }
}
