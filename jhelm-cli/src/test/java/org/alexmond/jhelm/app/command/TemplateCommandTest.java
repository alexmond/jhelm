package org.alexmond.jhelm.app.command;

import java.util.List;

import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.util.ValuesProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TemplateCommandTest {

	@Mock
	private TemplateAction templateAction;

	private TemplateCommand templateCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		templateCommand = new TemplateCommand(templateAction, new JhelmCoreProperties());
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

	private ValuesProfiles runAndCaptureProfiles(JhelmCoreProperties props, String... args) {
		TemplateCommand command = new TemplateCommand(templateAction, props);
		ArgumentCaptor<ValuesProfiles> captor = ArgumentCaptor.forClass(ValuesProfiles.class);
		when(templateAction.render(anyString(), anyString(), anyString(), anyMap(), captor.capture(), any(), anyList()))
			.thenReturn("---\n");
		new CommandLine(command).execute(args);
		return captor.getValue();
	}

}
