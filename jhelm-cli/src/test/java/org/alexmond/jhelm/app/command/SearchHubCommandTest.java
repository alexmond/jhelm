package org.alexmond.jhelm.app.command;

import java.io.IOException;
import java.util.List;

import org.alexmond.jhelm.core.action.SearchHubAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SearchHubCommandTest {

	@Mock
	private SearchHubAction searchHubAction;

	private SearchHubCommand searchHubCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		searchHubCommand = new SearchHubCommand(searchHubAction);
	}

	@Test
	void testSearchHubSuccess() throws Exception {
		SearchHubAction.HubResult result = new SearchHubAction.HubResult();
		result.setName("nginx");
		result.setDescription("A chart for nginx");
		result.setVersion("15.0.0");
		result.setAppVersion("1.25.0");
		result.setRepoName("bitnami");
		result.setRepoUrl("https://charts.bitnami.com/bitnami");

		when(searchHubAction.search(anyString(), anyInt())).thenReturn(List.of(result));

		CommandLine cmd = new CommandLine(searchHubCommand);
		int exitCode = cmd.execute("nginx");
		assertEquals(0, exitCode);
	}

	@Test
	void testSearchHubNoResults() throws Exception {
		when(searchHubAction.search(anyString(), anyInt())).thenReturn(List.of());

		CommandLine cmd = new CommandLine(searchHubCommand);
		int exitCode = cmd.execute("nonexistent");
		assertEquals(0, exitCode);
	}

	@Test
	void testSearchHubError() throws Exception {
		when(searchHubAction.search(anyString(), anyInt())).thenThrow(new IOException("connection failed"));

		CommandLine cmd = new CommandLine(searchHubCommand);
		int exitCode = cmd.execute("nginx");
		assertEquals(0, exitCode);
	}

}
