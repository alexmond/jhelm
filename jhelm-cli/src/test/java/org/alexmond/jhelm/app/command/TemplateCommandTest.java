package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.alexmond.jhelm.core.action.DependencyUpdateAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.config.ConfigServerProperties;
import org.alexmond.jhelm.core.service.ConfigServerValuesLoader;
import org.alexmond.jhelm.core.util.ValuesProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateCommandTest {

	@Mock
	private TemplateAction templateAction;

	@Mock
	private DependencyUpdateAction dependencyUpdateAction;

	private TemplateCommand templateCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		templateCommand = new TemplateCommand(templateAction, new JhelmCoreProperties(),
				new ConfigServerValuesLoader(new ConfigServerProperties(), null), dependencyUpdateAction);
	}

	@Test
	void testTemplateCommandSuccess() throws Exception {
		when(templateAction.render(anyString(), anyString(), anyString())).thenReturn("---\nkind: Service\n");

		CommandLine cmd = new CommandLine(templateCommand);
		cmd.execute("my-release", "/path/to/chart");
	}

	@Test
	void testTemplateCommandWithError() throws Exception {
		when(templateAction.render(anyString(), anyString(), anyString()))
			.thenThrow(new RuntimeException("Test error"));

		CommandLine cmd = new CommandLine(templateCommand);
		cmd.execute("my-release", "/path/to/chart");
	}

	@Test
	void testProfileFlagSplitsOnCommaAndReachesAction() {
		ValuesProfiles captured = runAndCaptureProfiles(new JhelmCoreProperties(), "-P", "prod,staging", "r", "/chart");
		assertEquals(List.of("prod", "staging"), captured.active(), "comma-separated --profile splits in order");
	}

	@Test
	void testActiveProfilesPropertyUsedWhenNoFlag() {
		JhelmCoreProperties props = new JhelmCoreProperties();
		props.getProfiles().setActive(List.of("dev"));
		ValuesProfiles captured = runAndCaptureProfiles(props, "r", "/chart");
		assertEquals(List.of("dev"), captured.active(), "jhelm.profiles.active applies when --profile is absent");
	}

	@Test
	void testProfileFlagOverridesProperty() {
		JhelmCoreProperties props = new JhelmCoreProperties();
		props.getProfiles().setActive(List.of("dev"));
		ValuesProfiles captured = runAndCaptureProfiles(props, "-P", "prod", "r", "/chart");
		assertEquals(List.of("prod"), captured.active(), "--profile takes precedence over the property");
	}

	private static final String MULTI_DOC = """
			# Source: mychart/templates/configmap.yaml
			kind: ConfigMap
			---
			# Source: mychart/templates/deployment.yaml
			kind: Deployment
			---
			# Source: mychart/templates/tests/test-connection.yaml
			kind: Pod
			metadata:
			  annotations:
			    "helm.sh/hook": test
			""";

	private void stubRender(String manifest) {
		when(templateAction.render(anyString(), anyString(), anyString(), anyMap(), any(), any(), anyList(),
				anyBoolean(), anyBoolean()))
			.thenReturn(manifest);
	}

	@Test
	void testIncludeCrdsAndIsUpgradeReachTheAction() {
		stubRender(MULTI_DOC);
		ArgumentCaptor<Boolean> isUpgrade = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<Boolean> includeCrds = ArgumentCaptor.forClass(Boolean.class);

		new CommandLine(templateCommand).execute("r", "/chart", "--is-upgrade", "--include-crds");

		verify(templateAction).render(anyString(), anyString(), anyString(), anyMap(), any(), any(), anyList(),
				isUpgrade.capture(), includeCrds.capture());
		assertEquals(true, isUpgrade.getValue());
		assertEquals(true, includeCrds.getValue());
	}

	@Test
	void testRenderControlFlagsDefaultFalse() {
		stubRender(MULTI_DOC);
		ArgumentCaptor<Boolean> isUpgrade = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<Boolean> includeCrds = ArgumentCaptor.forClass(Boolean.class);

		new CommandLine(templateCommand).execute("r", "/chart");

		verify(templateAction).render(anyString(), anyString(), anyString(), anyMap(), any(), any(), anyList(),
				isUpgrade.capture(), includeCrds.capture());
		assertEquals(false, isUpgrade.getValue());
		assertEquals(false, includeCrds.getValue());
	}

	@Test
	void testDependencyUpdateTriggersUpdateOnLocalChartDir(@TempDir Path chartDir) throws Exception {
		stubRender(MULTI_DOC);

		new CommandLine(templateCommand).execute("r", chartDir.toString(), "--dependency-update");

		verify(dependencyUpdateAction).update(any(File.class), any(), eq(false));
	}

	@Test
	void testShowOnlyFiltersRenderedOutput() {
		stubRender(MULTI_DOC);
		String out = captureStdout(() -> new CommandLine(templateCommand).execute("r", "/chart", "--show-only",
				"templates/deployment.yaml"));
		assertTrue(out.contains("kind: Deployment"), out);
		assertFalse(out.contains("kind: ConfigMap"), out);
	}

	@Test
	void testSkipTestsDropsTestHookDocument() {
		stubRender(MULTI_DOC);
		String out = captureStdout(() -> new CommandLine(templateCommand).execute("r", "/chart", "--skip-tests"));
		assertFalse(out.contains("kind: Pod"), out);
		assertTrue(out.contains("kind: ConfigMap"), out);
		assertTrue(out.contains("kind: Deployment"), out);
	}

	@Test
	void testOutputDirWritesPerSourceFiles(@TempDir Path dir) throws Exception {
		stubRender(MULTI_DOC);
		String out = captureStdout(
				() -> new CommandLine(templateCommand).execute("r", "/chart", "--output-dir", dir.toString()));
		Path deployment = dir.resolve("mychart/templates/deployment.yaml");
		assertTrue(Files.exists(deployment), "expected " + deployment + "; stdout:\n" + out);
		assertTrue(Files.readString(deployment).contains("kind: Deployment"));
		assertTrue(out.contains("wrote "), out);
	}

	private static String captureStdout(Runnable action) {
		PrintStream original = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
		try {
			action.run();
		}
		finally {
			System.setOut(original);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	private ValuesProfiles runAndCaptureProfiles(JhelmCoreProperties props, String... args) {
		TemplateCommand command = new TemplateCommand(templateAction, props,
				new ConfigServerValuesLoader(new ConfigServerProperties(), null), dependencyUpdateAction);
		ArgumentCaptor<ValuesProfiles> captor = ArgumentCaptor.forClass(ValuesProfiles.class);
		when(templateAction.render(anyString(), anyString(), anyString(), anyMap(), captor.capture(), any(), anyList(),
				anyBoolean(), anyBoolean()))
			.thenReturn("---\n");
		new CommandLine(command).execute(args);
		return captor.getValue();
	}

}
