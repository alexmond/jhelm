package org.alexmond.jhelm.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test that compares jhelm create output with helm create output
 * to ensure 100% compatibility with Helm 4.1.0.
 * <p>
 * Test artifacts are stored in target/test-charts/
 */
class CreateCommandTest {

    private final String chartName = "test-chart";
    @TempDir
    Path tempDir;
    private Path helmChartPath;
    private Path jhelmChartPath;

    @BeforeEach
    void setUp() throws IOException {
        // Create output directory in target
        Path targetDir = Path.of("target/test-charts");
        Files.createDirectories(targetDir);

        helmChartPath = targetDir.resolve("helm-" + chartName);
        jhelmChartPath = targetDir.resolve("jhelm-" + chartName);

        // Clean up any existing test charts
        if (Files.exists(helmChartPath)) {
            deleteDirectory(helmChartPath);
        }
        if (Files.exists(jhelmChartPath)) {
            deleteDirectory(jhelmChartPath);
        }
    }

    @Test
    void testCreateChartMatchesHelm() throws Exception {
        // Generate chart with helm
        createWithHelm();

        // Generate chart with jhelm
        createWithJHelm();

        // Compare directory structures
        List<String> helmFiles = getFileList(helmChartPath);
        List<String> jhelmFiles = getFileList(jhelmChartPath);

        assertEquals(helmFiles.size(), jhelmFiles.size(),
                "Number of files should match. Helm: " + helmFiles + ", JHelm: " + jhelmFiles);

        // Sort for comparison
        helmFiles.sort(String::compareTo);
        jhelmFiles.sort(String::compareTo);

        // Compare file lists
        for (int i = 0; i < helmFiles.size(); i++) {
            assertEquals(helmFiles.get(i), jhelmFiles.get(i),
                    "File structure should match at index " + i);
        }

        // Compare file contents
        List<String> differences = new ArrayList<>();
        for (String relPath : helmFiles) {
            Path helmFile = helmChartPath.resolve(relPath);
            Path jhelmFile = jhelmChartPath.resolve(relPath);

            String helmContent = Files.readString(helmFile);
            String jhelmContent = Files.readString(jhelmFile);

            if (!helmContent.equals(jhelmContent)) {
                differences.add(String.format("File %s differs:\nHelm:\n%s\n\nJHelm:\n%s\n",
                        relPath, helmContent, jhelmContent));
            }
        }

        if (!differences.isEmpty()) {
            fail("Found differences in files:\n" + String.join("\n---\n", differences));
        }
    }

    private void createWithHelm() throws IOException, InterruptedException {
        // Create temporary directory for helm to run in
        Path helmWorkDir = helmChartPath.getParent().resolve("helm-work");
        Files.createDirectories(helmWorkDir);

        ProcessBuilder pb = new ProcessBuilder("helm", "create", chartName);
        pb.directory(helmWorkDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                .lines().collect(Collectors.joining("\n"));

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            fail("helm create failed with exit code " + exitCode + ": " + output);
        }

        // Move the created chart to expected location
        Files.move(helmWorkDir.resolve(chartName), helmChartPath);
        deleteDirectory(helmWorkDir);
    }

    private void createWithJHelm() throws IOException, InterruptedException {
        // Create temporary directory for jhelm to run in
        Path jhelmWorkDir = jhelmChartPath.getParent().resolve("jhelm-work");
        Files.createDirectories(jhelmWorkDir);

        // Run jhelm via java -jar
        Path jarPath = Path.of("target/jhelm-app-0.0.1-SNAPSHOT.jar");

        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", jarPath.toAbsolutePath().toString(),
                "create", chartName
        );
        pb.directory(jhelmWorkDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                .lines().collect(Collectors.joining("\n"));

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            fail("jhelm create failed with exit code " + exitCode + ": " + output);
        }

        // Move the created chart to expected location
        Files.move(jhelmWorkDir.resolve(chartName), jhelmChartPath);
        deleteDirectory(jhelmWorkDir);
    }

    private List<String> getFileList(Path rootPath) throws IOException {
        List<String> files = new ArrayList<>();
        Files.walk(rootPath)
                .filter(Files::isRegularFile)
                .forEach(path -> files.add(rootPath.relativize(path).toString()));
        return files;
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted((a, b) -> -a.compareTo(b)) // Reverse order for deletion
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }
}
