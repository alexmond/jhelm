package org.alexmond.jhelm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CreateActionTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreateChartSuccess() throws IOException {
        CreateAction createAction = new CreateAction();
        Path chartPath = tempDir.resolve("my-chart");

        createAction.create(chartPath);

        // Verify directory structure
        assertTrue(Files.exists(chartPath));
        assertTrue(Files.exists(chartPath.resolve("templates")));
        assertTrue(Files.exists(chartPath.resolve("templates/tests")));

        // Verify files
        assertTrue(Files.exists(chartPath.resolve(".helmignore")));
        assertTrue(Files.exists(chartPath.resolve("Chart.yaml")));
        assertTrue(Files.exists(chartPath.resolve("values.yaml")));
        assertTrue(Files.exists(chartPath.resolve("templates/NOTES.txt")));
        assertTrue(Files.exists(chartPath.resolve("templates/_helpers.tpl")));
        assertTrue(Files.exists(chartPath.resolve("templates/deployment.yaml")));
        assertTrue(Files.exists(chartPath.resolve("templates/service.yaml")));
        assertTrue(Files.exists(chartPath.resolve("templates/serviceaccount.yaml")));
        assertTrue(Files.exists(chartPath.resolve("templates/hpa.yaml")));
        assertTrue(Files.exists(chartPath.resolve("templates/ingress.yaml")));
        assertTrue(Files.exists(chartPath.resolve("templates/httproute.yaml")));
        assertTrue(Files.exists(chartPath.resolve("templates/tests/test-connection.yaml")));

        // Verify chart name substitution in Chart.yaml
        String chartYaml = Files.readString(chartPath.resolve("Chart.yaml"));
        assertTrue(chartYaml.contains("name: my-chart"));
    }

    @Test
    void testCreateChartFailsWhenDirectoryExists() throws IOException {
        CreateAction createAction = new CreateAction();
        Path chartPath = tempDir.resolve("existing-chart");
        Files.createDirectories(chartPath);

        IOException exception = assertThrows(IOException.class, () -> {
            createAction.create(chartPath);
        });

        assertTrue(exception.getMessage().contains("already exists"));
    }
}
