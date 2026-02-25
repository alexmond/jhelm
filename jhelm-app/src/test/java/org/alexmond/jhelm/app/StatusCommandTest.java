package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.action.StatusAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class StatusCommandTest {

	@Mock
	private StatusAction statusAction;

	private StatusCommand statusCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		statusCommand = new StatusCommand(statusAction);
	}

	@Test
	void testStatusCommandWithExistingRelease() throws Exception {
		Release release = createMockRelease();
		when(statusAction.status(anyString(), anyString())).thenReturn(Optional.of(release));

		CommandLine cmd = new CommandLine(statusCommand);
		cmd.execute("my-release", "-n", "default");
	}

	@Test
	void testStatusCommandWithNonExistingRelease() throws Exception {
		when(statusAction.status(anyString(), anyString())).thenReturn(Optional.empty());

		CommandLine cmd = new CommandLine(statusCommand);
		cmd.execute("non-existing-release");
	}

	@Test
	void testStatusCommandWithError() throws Exception {
		when(statusAction.status(anyString(), anyString())).thenThrow(new RuntimeException("Test error"));

		CommandLine cmd = new CommandLine(statusCommand);
		cmd.execute("my-release");
	}

	@Test
	void testStatusCommandWithShowResources() throws Exception {
		Release release = createMockRelease();
		when(statusAction.status(anyString(), anyString())).thenReturn(Optional.of(release));

		List<ResourceStatus> statuses = List.of(
				ResourceStatus.builder()
					.kind("Deployment")
					.name("my-deploy")
					.namespace("default")
					.ready(true)
					.message("3/3 replicas ready")
					.build(),
				ResourceStatus.builder()
					.kind("Service")
					.name("my-svc")
					.namespace("default")
					.ready(false)
					.message("pending")
					.build());
		when(statusAction.getResourceStatuses(any(Release.class))).thenReturn(statuses);

		CommandLine cmd = new CommandLine(statusCommand);
		cmd.execute("my-release", "--show-resources");
	}

	@Test
	void testStatusCommandWithShowResourcesEmpty() throws Exception {
		Release release = createMockRelease();
		when(statusAction.status(anyString(), anyString())).thenReturn(Optional.of(release));
		when(statusAction.getResourceStatuses(any(Release.class))).thenReturn(List.of());

		CommandLine cmd = new CommandLine(statusCommand);
		cmd.execute("my-release", "--show-resources");
	}

	private Release createMockRelease() {
		ChartMetadata metadata = ChartMetadata.builder().name("test-chart").version("1.0.0").build();

		Chart chart = Chart.builder().metadata(metadata).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.status("deployed")
			.lastDeployed(OffsetDateTime.now())
			.build();

		return Release.builder()
			.name("my-release")
			.namespace("default")
			.version(1)
			.chart(chart)
			.info(info)
			.manifest("---\nkind: Service\n")
			.build();
	}

}
