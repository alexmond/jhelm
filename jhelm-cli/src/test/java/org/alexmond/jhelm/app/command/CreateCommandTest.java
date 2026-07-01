package org.alexmond.jhelm.app.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.alexmond.jhelm.core.action.CreateAction;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@code jhelm create}. The scaffold is exercised <strong>in-process</strong>
 * via {@link CreateAction} so the test always runs — it no longer depends on a packaged
 * jar that isn't built during the {@code test} phase. The old hard-coded
 * {@code target/jhelm-0.0.1-SNAPSHOT.jar} path made the whole test silently return green
 * without asserting anything.
 * <p>
 * A second test cross-checks byte-for-byte against {@code helm create}, but only when a
 * <strong>helm 4.x</strong> CLI is on {@code PATH}: jhelm scaffolds the Helm 4 template
 * set, so comparing against a helm 3.x binary would diff on template drift. When no
 * compatible helm is present it {@linkplain Assumptions skips visibly} rather than
 * passing silently.
 */
class CreateCommandTest {

	private static final List<String> EXPECTED_FILES = List.of(".helmignore", "Chart.yaml", "values.yaml",
			"templates/NOTES.txt", "templates/_helpers.tpl", "templates/deployment.yaml", "templates/service.yaml",
			"templates/serviceaccount.yaml", "templates/hpa.yaml", "templates/ingress.yaml", "templates/httproute.yaml",
			"templates/tests/test-connection.yaml");

	private final String chartName = "test-chart";

	@TempDir
	Path tempDir;

	@Test
	void createScaffoldsExpectedChartInProcess() throws IOException {
		Path chartPath = tempDir.resolve(chartName);
		new CreateAction().create(chartPath);

		List<String> actual = getFileList(chartPath);
		List<String> expected = new ArrayList<>(EXPECTED_FILES);
		actual.sort(String::compareTo);
		expected.sort(String::compareTo);
		assertEquals(expected, actual, "Scaffolded file set should match the Helm chart layout");

		// the chart name is substituted into the generated files
		assertTrue(Files.readString(chartPath.resolve("Chart.yaml")).contains(chartName),
				"Chart.yaml should carry the chart name");
		assertTrue(Files.readString(chartPath.resolve("templates/_helpers.tpl")).contains(chartName),
				"_helpers.tpl should carry the chart name");
	}

	@Test
	void createMatchesHelm() throws Exception {
		Assumptions.assumeTrue(helmMajorVersion() == 4,
				"helm 4.x not on PATH — skipping jhelm/helm create byte-for-byte parity");

		Path targetDir = Path.of("target/test-charts");
		Files.createDirectories(targetDir);
		Path helmChartPath = targetDir.resolve("helm-" + chartName);
		Path jhelmChartPath = targetDir.resolve("jhelm-" + chartName);
		deleteDirectory(helmChartPath);
		deleteDirectory(jhelmChartPath);

		createWithHelm(helmChartPath);
		new CreateAction().create(jhelmChartPath);

		List<String> helmFiles = getFileList(helmChartPath);
		List<String> jhelmFiles = getFileList(jhelmChartPath);
		helmFiles.sort(String::compareTo);
		jhelmFiles.sort(String::compareTo);
		assertEquals(helmFiles, jhelmFiles, "File structure should match helm");

		List<String> differences = new ArrayList<>();
		for (String relPath : helmFiles) {
			String helmContent = Files.readString(helmChartPath.resolve(relPath));
			String jhelmContent = Files.readString(jhelmChartPath.resolve(relPath));
			if (!helmContent.equals(jhelmContent)) {
				differences.add(String.format("File %s differs:%nHelm:%n%s%n%nJHelm:%n%s%n", relPath, helmContent,
						jhelmContent));
			}
		}
		if (!differences.isEmpty()) {
			fail("Found differences in files:\n" + String.join("\n---\n", differences));
		}
	}

	private void createWithHelm(Path helmChartPath) throws IOException, InterruptedException {
		Path helmWorkDir = helmChartPath.getParent().resolve("helm-work");
		deleteDirectory(helmWorkDir);
		Files.createDirectories(helmWorkDir);

		ProcessBuilder pb = new ProcessBuilder("helm", "create", chartName);
		pb.directory(helmWorkDir.toFile());
		pb.redirectErrorStream(true);
		Process process = pb.start();
		String output = readProcessOutput(process);
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			fail("helm create failed with exit code " + exitCode + ": " + output);
		}
		Files.move(helmWorkDir.resolve(chartName), helmChartPath);
		deleteDirectory(helmWorkDir);
	}

	/**
	 * Returns the major version of the {@code helm} CLI on {@code PATH}, or {@code -1} if
	 * helm is absent or its version can't be parsed.
	 */
	private int helmMajorVersion() {
		try {
			Process process = new ProcessBuilder("helm", "version", "--short").redirectErrorStream(true).start();
			String output = readProcessOutput(process);
			if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
				return -1;
			}
			// e.g. "v3.19.0+g3d8990f" -> 3
			String trimmed = output.strip();
			if (trimmed.startsWith("v")) {
				trimmed = trimmed.substring(1);
			}
			return Integer.parseInt(trimmed.substring(0, trimmed.indexOf('.')));
		}
		catch (IOException | InterruptedException | RuntimeException ex) {
			return -1;
		}
	}

	private String readProcessOutput(Process process) throws IOException {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		}
	}

	private List<String> getFileList(Path rootPath) throws IOException {
		try (var walk = Files.walk(rootPath)) {
			return walk.filter(Files::isRegularFile)
				.map((path) -> rootPath.relativize(path).toString())
				.collect(Collectors.toCollection(ArrayList::new));
		}
	}

	private void deleteDirectory(Path path) throws IOException {
		if (Files.exists(path)) {
			try (var walk = Files.walk(path)) {
				walk.sorted((a, b) -> -a.compareTo(b)).forEach((p) -> {
					try {
						Files.delete(p);
					}
					catch (IOException ex) {
						// best-effort cleanup
					}
				});
			}
		}
	}

}
