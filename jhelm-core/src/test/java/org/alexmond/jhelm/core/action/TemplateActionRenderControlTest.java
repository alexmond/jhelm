package org.alexmond.jhelm.core.action;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.util.ValuesProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-engine coverage for the {@code --include-crds} and {@code --is-upgrade} render
 * controls on {@link TemplateAction}.
 */
class TemplateActionRenderControlTest {

	private final TemplateAction templateAction = new TemplateAction(new Engine(), new ChartLoader());

	@TempDir
	Path chartDir;

	@BeforeEach
	void writeChart() throws Exception {
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: crdchart
				version: 1.0.0
				""");
		Path templates = Files.createDirectories(chartDir.resolve("templates"));
		Files.writeString(templates.resolve("cm.yaml"), """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: cm
				data:
				  upgrade: "{{ .Release.IsUpgrade }}"
				""");
		Files.writeString(templates.resolve("svc.yaml"), """
				apiVersion: v1
				kind: Service
				metadata:
				  name: svc
				""");
		Path tests = Files.createDirectories(templates.resolve("tests"));
		Files.writeString(tests.resolve("test-connection.yaml"), """
				apiVersion: v1
				kind: Pod
				metadata:
				  name: test
				  annotations:
				    "helm.sh/hook": test
				""");
		Path crds = Files.createDirectories(chartDir.resolve("crds"));
		Files.writeString(crds.resolve("widget.yaml"), """
				apiVersion: apiextensions.k8s.io/v1
				kind: CustomResourceDefinition
				metadata:
				  name: widgets.example.com
				""");
	}

	private String render(boolean isUpgrade, boolean includeCrds) {
		return templateAction.render(chartDir.toString(), "r", "default", new HashMap<>(), ValuesProfiles.none(), null,
				List.of(), isUpgrade, includeCrds);
	}

	@Test
	void testCrdsExcludedByDefault() {
		String manifest = render(false, false);
		assertFalse(manifest.contains("CustomResourceDefinition"), manifest);
		assertTrue(manifest.contains("kind: ConfigMap"), manifest);
	}

	@Test
	void testIncludeCrdsEmitsCrdWithSourceMarker() {
		String manifest = render(false, true);
		assertTrue(manifest.contains("# Source: crdchart/crds/widget.yaml"), manifest);
		assertTrue(manifest.contains("kind: CustomResourceDefinition"), manifest);
		assertTrue(manifest.contains("kind: ConfigMap"), manifest);
	}

	@Test
	void testIsInstallByDefault() {
		assertTrue(render(false, false).contains("upgrade: \"false\""), "default posture is install");
	}

	@Test
	void testIsUpgradeFlipsReleaseFlag() {
		assertTrue(render(true, false).contains("upgrade: \"true\""), "--is-upgrade sets .Release.IsUpgrade");
	}

	@Test
	void testRenderWithControlsSkipTestsDropsTestHook() {
		String manifest = templateAction.renderWithControls(chartDir.toString(), "r", "default", new HashMap<>(), false,
				false, true, List.of());
		assertFalse(manifest.contains("kind: Pod"), manifest);
		assertTrue(manifest.contains("kind: ConfigMap"), manifest);
	}

	@Test
	void testRenderWithControlsShowOnlySelectsTemplate() {
		String manifest = templateAction.renderWithControls(chartDir.toString(), "r", "default", new HashMap<>(), false,
				false, false, List.of("templates/svc.yaml"));
		assertTrue(manifest.contains("kind: Service"), manifest);
		assertFalse(manifest.contains("kind: ConfigMap"), manifest);
	}

	@Test
	void testRenderWithControlsIncludeCrdsAndSkipTests() {
		String manifest = templateAction.renderWithControls(chartDir.toString(), "r", "default", new HashMap<>(), false,
				true, true, List.of());
		assertTrue(manifest.contains("kind: CustomResourceDefinition"), manifest);
		assertFalse(manifest.contains("kind: Pod"), manifest);
	}

}
