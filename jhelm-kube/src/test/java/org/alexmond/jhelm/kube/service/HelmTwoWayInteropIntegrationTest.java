package org.alexmond.jhelm.kube.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.alexmond.jhelm.core.CoreConfig;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.kube.KubeClusterAvailable;
import org.alexmond.jhelm.kube.KubernetesConfig;
import org.alexmond.jhelm.kube.service.internal.HelmKubeService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves release-storage interoperability with the real {@code helm} CLI in both
 * directions: a release written by jhelm is readable and manageable by {@code helm}, and
 * a release written by {@code helm} is readable by jhelm. This is the two-way counterpart
 * to {@link HelmFullFlowIntegrationTest} (which exercises jhelm alone). It needs a
 * reachable cluster ({@link KubeClusterAvailable}) and the {@code helm} binary on
 * {@code PATH} — CI provides both (kind + azure/setup-helm); it skips cleanly otherwise.
 */
@SpringBootTest(classes = { KubernetesConfig.class, HelmKubeService.class, CoreConfig.class })
@KubeClusterAvailable
@Slf4j
class HelmTwoWayInteropIntegrationTest {

	private static final String NAMESPACE = "default";

	@Autowired
	private HelmKubeService helmKubeService;

	@Autowired
	private InstallAction installAction;

	@Autowired
	private UninstallAction uninstallAction;

	@Test
	void helmCanReadAndListAReleaseInstalledByJhelm() throws Exception {
		Assumptions.assumeTrue(helmAvailable(), "helm CLI not on PATH");
		String name = "interop-j2h";
		try {
			installAction.install(InstallOptions.builder()
				.chart(configMapChart("interopj2h"))
				.releaseName(name)
				.namespace(NAMESPACE)
				.values(Map.of("foo", "from-jhelm"))
				.revision(1)
				.dryRun(false)
				.build());

			// helm list reads the Secret labels jhelm wrote
			HelmResult list = helm("list", "-n", NAMESPACE, "-o", "json");
			assertEquals(0, list.exitCode(), list.output());
			assertTrue(list.output().contains(name), list.output());
			assertTrue(list.output().contains("deployed"), list.output());

			// helm get values reads the release payload's bare `config` map (#608)
			HelmResult values = helm("get", "values", name, "-n", NAMESPACE, "-o", "json");
			assertEquals(0, values.exitCode(), values.output());
			assertTrue(values.output().contains("from-jhelm"), values.output());

			// helm status parses the full release record
			HelmResult status = helm("status", name, "-n", NAMESPACE);
			assertEquals(0, status.exitCode(), status.output());
		}
		finally {
			cleanup(name);
		}
	}

	@Test
	void jhelmCanReadAReleaseInstalledByHelm(@TempDir Path tempDir) throws Exception {
		Assumptions.assumeTrue(helmAvailable(), "helm CLI not on PATH");
		String name = "interop-h2j";
		Path chartDir = writeConfigMapChart(tempDir, "interoph2j");
		try {
			HelmResult install = helm("install", name, chartDir.toString(), "-n", NAMESPACE, "--set", "foo=from-helm");
			assertEquals(0, install.exitCode(), install.output());

			// jhelm decodes helm's release Secret (payload #608 + embedded chart #609)
			Optional<Release> stored = helmKubeService.getRelease(name, NAMESPACE);
			assertTrue(stored.isPresent(), "jhelm could not read the helm-installed release");
			Release release = stored.get();
			assertEquals(1, release.getVersion());
			assertEquals(ReleaseStatus.DEPLOYED, release.getInfo().getStatus());
			assertEquals("from-helm", release.getConfig().getValues().get("foo"));
			// the embedded chart round-trips back to raw text jhelm can render
			assertTrue(release.getChart().getTemplates().stream().anyMatch((t) -> t.getData().contains("ConfigMap")),
					"embedded chart templates not decoded to raw text");
		}
		finally {
			helm("uninstall", name, "-n", NAMESPACE);
		}
	}

	@Test
	void helmCanUpgradeAReleaseInstalledByJhelm(@TempDir Path tempDir) throws Exception {
		Assumptions.assumeTrue(helmAvailable(), "helm CLI not on PATH");
		String name = "interop-upgrade";
		Path chartDir = writeConfigMapChart(tempDir, "interopupgrade");
		try {
			// jhelm writes revision 1 (helm must parse it, incl. the embedded chart, to
			// upgrade)
			installAction.install(InstallOptions.builder()
				.chart(configMapChart("interopupgrade"))
				.releaseName(name)
				.namespace(NAMESPACE)
				.values(Map.of("foo", "v1"))
				.revision(1)
				.dryRun(false)
				.build());

			HelmResult upgrade = helm("upgrade", name, chartDir.toString(), "-n", NAMESPACE, "--set", "foo=v2");
			assertEquals(0, upgrade.exitCode(),
					"helm could not upgrade a jhelm-installed release: " + upgrade.output());

			// jhelm reads the helm-written revision 2 back
			Optional<Release> stored = helmKubeService.getRelease(name, NAMESPACE);
			assertTrue(stored.isPresent());
			assertEquals(2, stored.get().getVersion());
			assertEquals(ReleaseStatus.DEPLOYED, stored.get().getInfo().getStatus());
			assertEquals("v2", stored.get().getConfig().getValues().get("foo"));
		}
		finally {
			helm("uninstall", name, "-n", NAMESPACE);
			cleanup(name);
		}
	}

	private Chart configMapChart(String chartName) {
		String template = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: {{ .Release.Name }}-cm
				data:
				  foo: {{ .Values.foo | quote }}
				""";
		return Chart.builder()
			.metadata(ChartMetadata.builder().name(chartName).version("0.1.0").apiVersion("v2").build())
			.templates(new ArrayList<>(List.of(Chart.Template.builder().name("cm.yaml").data(template).build())))
			.values(Map.of("foo", "default"))
			.build();
	}

	private Path writeConfigMapChart(Path dir, String chartName) throws IOException {
		Files.writeString(dir.resolve("Chart.yaml"), "apiVersion: v2\nname: " + chartName + "\nversion: 0.1.0\n");
		Files.createDirectories(dir.resolve("templates"));
		Files.writeString(dir.resolve("templates").resolve("cm.yaml"), """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: {{ .Release.Name }}-cm
				data:
				  foo: {{ .Values.foo | quote }}
				""");
		Files.writeString(dir.resolve("values.yaml"), "foo: default\n");
		return dir;
	}

	private void cleanup(String name) {
		try {
			this.uninstallAction.uninstall(UninstallOptions.builder().releaseName(name).namespace(NAMESPACE).build());
		}
		catch (RuntimeException ex) {
			log.debug("cleanup of {} failed (may already be gone): {}", name, ex.getMessage());
		}
	}

	private boolean helmAvailable() {
		try {
			return helm("version", "--short").exitCode() == 0;
		}
		catch (Exception ex) {
			return false;
		}
	}

	private HelmResult helm(String... args) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add("helm");
		command.addAll(List.of(args));
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (!process.waitFor(90, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IllegalStateException("helm timed out: " + command);
		}
		return new HelmResult(process.exitValue(), output);
	}

	private record HelmResult(int exitCode, String output) {
	}

}
