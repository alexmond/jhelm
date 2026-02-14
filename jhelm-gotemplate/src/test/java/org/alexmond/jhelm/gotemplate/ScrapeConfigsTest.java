package org.alexmond.jhelm.gotemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScrapeConfigsTest {

    @Test
    void testScrapeConfigsCondition() throws Exception {
        // Load values
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        File valuesFile = new File("/Users/alex.mondshain/IdeaProjects/jhelm/test-scrapeconfigs.yaml");
        Map<String, Object> values = yamlMapper.readValue(valuesFile, Map.class);

        System.out.println("Values: " + values);
        System.out.println("scrapeConfigs exists: " + values.containsKey("scrapeConfigs"));
        System.out.println("scrapeConfigs value: " + values.get("scrapeConfigs"));

        // Load template
        String templateContent = Files.readString(new File("/Users/alex.mondshain/IdeaProjects/jhelm/test-template.yaml").toPath());

        // Parse and execute
        GoTemplateFactory factory = new GoTemplateFactory();
        factory.parse("test", templateContent);
        GoTemplate template = factory.getTemplate("test");

        Map<String, Object> context = Map.of("Values", values);
        StringWriter writer = new StringWriter();
        template.execute(context, writer);

        String result = writer.toString();
        System.out.println("Result:\n" + result);

        assertTrue(result.contains("scrape_configs:"), "Should generate scrape_configs section");
        assertTrue(result.contains("job_name: prometheus"), "Should include prometheus job");
        assertTrue(result.contains("job_name: kubernetes-api-servers"), "Should include kubernetes-api-servers job");
    }
}
