package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.action.ListAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ListCommandTest {

	@Mock
	private ListAction listAction;

	private ListCommand listCommand;

	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

	private final PrintStream originalOut = System.out;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		listCommand = new ListCommand(listAction);
		System.setOut(new PrintStream(outputStream, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
	}

	private String captured() {
		return outputStream.toString(StandardCharsets.UTF_8);
	}

	@Test
	void testListCommandWithReleases() throws Exception {
		when(listAction.list(anyString()))
			.thenReturn(Arrays.asList(createMockRelease("release1", 1), createMockRelease("release2", 2)));

		CommandLine cmd = new CommandLine(listCommand);
		cmd.execute("-n", "default");
	}

	@Test
	void testListCommandWithNoReleases() throws Exception {
		when(listAction.list(anyString())).thenReturn(Collections.emptyList());

		CommandLine cmd = new CommandLine(listCommand);
		cmd.execute();
	}

	@Test
	void testListCommandWithError() throws Exception {
		when(listAction.list(anyString())).thenThrow(new RuntimeException("Test error"));

		CommandLine cmd = new CommandLine(listCommand);
		cmd.execute();
	}

	@Test
	void testListCommandJsonOutput() throws Exception {
		when(listAction.list(anyString())).thenReturn(Arrays.asList(createMockRelease("release1", 3)));

		new CommandLine(listCommand).execute("-o", "json");

		String out = captured();
		// Helm-style snake_case keys, and the release fields
		assertTrue(out.contains("\"name\":\"release1\""), out);
		assertTrue(out.contains("\"revision\":3"), out);
		assertTrue(out.contains("\"status\":\"deployed\""), out);
		assertTrue(out.contains("\"chart\":\"test-chart-1.0.0\""), out);
		assertTrue(out.contains("\"app_version\""), out);
	}

	@Test
	void testListCommandYamlOutput() throws Exception {
		when(listAction.list(anyString())).thenReturn(Arrays.asList(createMockRelease("release1", 1)));

		new CommandLine(listCommand).execute("--output", "yaml");

		String out = captured();
		assertTrue(out.contains("name: \"release1\""), out);
		assertTrue(out.contains("status: \"deployed\""), out);
	}

	private Release createMockRelease(String name, int version) {
		ChartMetadata metadata = ChartMetadata.builder().name("test-chart").version("1.0.0").build();

		Chart chart = Chart.builder().metadata(metadata).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder().status(ReleaseStatus.DEPLOYED).build();

		return Release.builder().name(name).namespace("default").version(version).chart(chart).info(info).build();
	}

}
