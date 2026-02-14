package org.alexmond.jhelm.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for YAML document separator handling in Engine.cleanManifest()
 * This is a recurring issue - test various edge cases
 */
class YamlDocumentSeparatorTest {

    private final Engine engine = new Engine();

    /**
     * Helper to call private cleanManifest method via reflection
     */
    private String cleanManifest(String manifest) throws Exception {
        Method method = Engine.class.getDeclaredMethod("cleanManifest", String.class);
        method.setAccessible(true);
        return (String) method.invoke(engine, manifest);
    }

    @Test
    void testBasicSeparator() throws Exception {
        String input = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test1
            ---
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test2
            """;

        String result = cleanManifest(input);

        // Should have exactly one separator between two documents
        long separatorCount = result.lines().filter(line -> line.equals("---")).count();
        assertEquals(1, separatorCount, "Should have exactly 1 separator");

        // Should have both ConfigMaps
        assertTrue(result.contains("name: test1"));
        assertTrue(result.contains("name: test2"));
    }

    @Test
    void testSeparatorWithComment() throws Exception {
        String input = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test1
            ---
            # This is a comment
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test2
            """;

        String result = cleanManifest(input);

        // Should preserve comment and have proper separator
        assertTrue(result.contains("# This is a comment"));
        assertTrue(result.contains("name: test1"));
        assertTrue(result.contains("name: test2"));

        // Should have exactly one separator
        long separatorCount = result.lines().filter(line -> line.equals("---")).count();
        assertEquals(1, separatorCount);
    }

    @Test
    void testMultilineStringWithDashes() throws Exception {
        String input = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test1
            data:
              content: |-
                This is a multiline string
                ---
                It contains three dashes that look like separator
                But they should NOT split the document
            ---
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test2
            """;

        String result = cleanManifest(input);

        // Should have both ConfigMaps
        assertTrue(result.contains("name: test1"));
        assertTrue(result.contains("name: test2"));

        // Should preserve the --- inside the multiline string
        assertTrue(result.contains("It contains three dashes"));

        // Should have exactly one ACTUAL separator (not counting the one inside the string)
        // This is tricky - the separator inside |- block should be preserved
        String[] docs = result.split("\\n---\\n");
        assertEquals(2, docs.length, "Should split into exactly 2 documents");
    }

    @Test
    void testEmptyDocuments() throws Exception {
        String input = """
            ---
            ---
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test1
            ---
            ---
            """;

        String result = cleanManifest(input);

        // Empty documents should be filtered out
        assertTrue(result.contains("name: test1"));

        // Should not have leading or trailing separators
        assertFalse(result.startsWith("---"));
        assertTrue(result.endsWith("\n")); // Should end with newline
    }

    @Test
    void testCertManagerStyleSeparator() throws Exception {
        // Simulates the cert-manager issue where {{- if -}} creates ---# pattern
        String input = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test1
            ---
            # Permission comment
            apiVersion: v1
            kind: ClusterRole
            metadata:
              name: test2
            """;

        String result = cleanManifest(input);

        // Should handle ---\n# pattern correctly
        assertTrue(result.contains("name: test1"));
        assertTrue(result.contains("# Permission comment"));
        assertTrue(result.contains("name: test2"));
        assertTrue(result.contains("kind: ClusterRole"));

        long separatorCount = result.lines().filter(line -> line.equals("---")).count();
        assertEquals(1, separatorCount);
    }

    @Test
    void testTrailingSeparator() throws Exception {
        String input = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test1
            ---
            """;

        String result = cleanManifest(input);

        // Trailing separator should be removed
        assertTrue(result.contains("name: test1"));
        assertFalse(result.trim().endsWith("---"));
    }

    @Test
    void testGrafanaDashboardWithBlankLines() throws Exception {
        // Simulates kube-prometheus-stack Grafana dashboard structure
        String input = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: dashboard-1
              labels:
                app: grafana

                chart: kube-prometheus-stack
            data:
              dashboard.json: |-
                {"panels":[{"id":1}]}
            ---
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: dashboard-2
            """;

        String result = cleanManifest(input);

        // Should handle blank lines in labels section
        assertTrue(result.contains("name: dashboard-1"));
        assertTrue(result.contains("name: dashboard-2"));
        assertTrue(result.contains("app: grafana"));
        assertTrue(result.contains("chart: kube-prometheus-stack"));

        long separatorCount = result.lines().filter(line -> line.equals("---")).count();
        assertEquals(1, separatorCount, "Should have exactly 1 separator");
    }

    @Test
    void testMultipleSeparatorsInRow() throws Exception {
        String input = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test1
            ---
            ---
            ---
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test2
            """;

        String result = cleanManifest(input);

        // Multiple separators should be collapsed to one
        long separatorCount = result.lines().filter(line -> line.equals("---")).count();
        assertEquals(1, separatorCount, "Multiple separators should collapse to one");
    }

    @Test
    void testSeparatorAtStart() throws Exception {
        String input = """
            ---
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test1
            ---
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test2
            """;

        String result = cleanManifest(input);

        // Leading separator should be removed
        assertFalse(result.startsWith("---"), "Should not start with separator");
        assertTrue(result.contains("name: test1"));
        assertTrue(result.contains("name: test2"));
    }

    @Test
    void testJsonWithDashes() throws Exception {
        // Grafana dashboards contain JSON with potential --- patterns
        String input = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: dashboard
            data:
              content.json: |-
                {
                  "description": "Some text --- with dashes",
                  "title": "Dashboard"
                }
            ---
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test2
            """;

        String result = cleanManifest(input);

        // JSON content should be preserved
        assertTrue(result.contains("Some text --- with dashes"));
        assertTrue(result.contains("name: dashboard"));
        assertTrue(result.contains("name: test2"));

        long separatorCount = result.lines().filter(line -> line.equals("---")).count();
        assertEquals(1, separatorCount, "Should have exactly 1 separator between documents");
    }
}
