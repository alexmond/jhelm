package org.alexmond.jhelm.gotemplate.helm;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test Helm 4 new functions: mustToYaml, mustToJson, mustFromYaml, mustFromJson
 */
public class Helm4FunctionsTest {

    @Test
    public void testMustToYaml() throws Exception {
        GoTemplate template = new GoTemplate();

        Map<String, Object> data = new HashMap<>();
        data.put("name", "nginx");
        data.put("replicas", 3);

        template.parse("test", "{{ .data | mustToYaml }}");
        StringWriter writer = new StringWriter();
        template.execute("test", Map.of("data", data), writer);

        String result = writer.toString().trim();
        System.out.println("testMustToYaml result: [" + result + "]");
        assertTrue(result.contains("name:") && result.contains("nginx"), "Result should contain 'name:' and 'nginx'");
        assertTrue(result.contains("replicas:") && result.contains("3"), "Result should contain 'replicas:' and '3'");
    }

    @Test
    public void testMustToJson() throws Exception {
        GoTemplate template = new GoTemplate();

        Map<String, Object> data = new HashMap<>();
        data.put("name", "nginx");
        data.put("replicas", 3);

        template.parse("test", "{{ .data | mustToJson }}");
        StringWriter writer = new StringWriter();
        template.execute("test", Map.of("data", data), writer);

        String result = writer.toString();
        assertTrue(result.contains("\"name\":\"nginx\"") || result.contains("\"name\": \"nginx\""));
        assertTrue(result.contains("\"replicas\":3") || result.contains("\"replicas\": 3"));
    }

    @Test
    public void testMustFromYaml() throws Exception {
        GoTemplate template = new GoTemplate();

        String yaml = "name: nginx\nreplicas: 3";

        template.parse("test", "{{ .yaml | mustFromYaml | toJson }}");
        StringWriter writer = new StringWriter();
        template.execute("test", Map.of("yaml", yaml), writer);

        String result = writer.toString();
        assertTrue(result.contains("\"name\":\"nginx\"") || result.contains("\"name\": \"nginx\""));
    }

    @Test
    public void testMustFromJson() throws Exception {
        GoTemplate template = new GoTemplate();

        String json = "{\"name\":\"nginx\",\"replicas\":3}";

        template.parse("test", "{{ .json | mustFromJson | toYaml }}");
        StringWriter writer = new StringWriter();
        template.execute("test", Map.of("json", json), writer);

        String result = writer.toString().trim();
        System.out.println("testMustFromJson result: [" + result + "]");
        assertTrue(result.contains("name:") && result.contains("nginx"), "Result should contain 'name:' and 'nginx'");
    }

    @Test
    public void testMustToYamlFailure() {
        GoTemplate template = new GoTemplate();

        assertThrows(Exception.class, () -> {
            template.parse("test", "{{ mustToYaml }}");
            StringWriter writer = new StringWriter();
            template.execute("test", Map.of(), writer);
        });
    }

    @Test
    public void testMustFromYamlFailure() {
        GoTemplate template = new GoTemplate();

        assertThrows(Exception.class, () -> {
            template.parse("test", "{{ \"invalid: [yaml\" | mustFromYaml }}");
            StringWriter writer = new StringWriter();
            template.execute("test", Map.of(), writer);
        });
    }

    @Test
    public void testToYamlStillWorks() throws Exception {
        GoTemplate template = new GoTemplate();

        Map<String, Object> data = new HashMap<>();
        data.put("name", "nginx");

        template.parse("test", "{{ .data | toYaml }}");
        StringWriter writer = new StringWriter();
        template.execute("test", Map.of("data", data), writer);

        String result = writer.toString().trim();
        System.out.println("testToYamlStillWorks result: [" + result + "]");
        assertTrue(result.contains("name:") && result.contains("nginx"), "Result should contain 'name:' and 'nginx'");
    }

    @Test
    public void testToJsonStillWorks() throws Exception {
        GoTemplate template = new GoTemplate();

        Map<String, Object> data = new HashMap<>();
        data.put("name", "nginx");

        template.parse("test", "{{ .data | toJson }}");
        StringWriter writer = new StringWriter();
        template.execute("test", Map.of("data", data), writer);

        String result = writer.toString();
        assertTrue(result.contains("\"name\""));
    }

    @Test
    public void testCategoryBasedOrganization() {
        // Test that we can get all Helm functions from the refactored structure
        GoTemplate template = new GoTemplate();
        Map<String, org.alexmond.jhelm.gotemplate.Function> helmFunctions = HelmFunctions.getFunctions(template);

        // Verify new Helm 4 functions are present
        assertTrue(helmFunctions.containsKey("mustToYaml"), "mustToYaml function should be available");
        assertTrue(helmFunctions.containsKey("mustToJson"), "mustToJson function should be available");
        assertTrue(helmFunctions.containsKey("mustFromYaml"), "mustFromYaml function should be available");
        assertTrue(helmFunctions.containsKey("mustFromJson"), "mustFromJson function should be available");

        // Verify existing functions still work
        assertTrue(helmFunctions.containsKey("toYaml"), "toYaml function should still be available");
        assertTrue(helmFunctions.containsKey("toJson"), "toJson function should still be available");
        assertTrue(helmFunctions.containsKey("include"), "include function should be available");
        assertTrue(helmFunctions.containsKey("tpl"), "tpl function should be available");
        assertTrue(helmFunctions.containsKey("required"), "required function should be available");
    }

    @Test
    public void testHelmFunctionCategories() {
        Map<String, java.util.List<String>> categories = HelmFunctions.getFunctionCategories();

        // Verify categories exist
        assertTrue(categories.containsKey("Template"));
        assertTrue(categories.containsKey("Conversion"));
        assertTrue(categories.containsKey("Kubernetes"));
        assertTrue(categories.containsKey("Chart"));

        // Verify Conversion category contains new Helm 4 functions
        assertTrue(categories.get("Conversion").contains("mustToYaml"));
        assertTrue(categories.get("Conversion").contains("mustToJson"));
        assertTrue(categories.get("Conversion").contains("mustFromYaml"));
        assertTrue(categories.get("Conversion").contains("mustFromJson"));
    }
}
