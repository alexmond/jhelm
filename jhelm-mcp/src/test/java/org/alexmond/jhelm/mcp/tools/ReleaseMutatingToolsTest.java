package org.alexmond.jhelm.mcp.tools;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.Map;

import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.service.CascadePolicy;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseMutatingToolsTest {

	@Mock
	private InstallAction installAction;

	@Mock
	private UpgradeAction upgradeAction;

	@Mock
	private UninstallAction uninstallAction;

	@Mock
	private RollbackAction rollbackAction;

	@Mock
	private TestAction testAction;

	@Mock
	private GetAction getAction;

	@Mock
	private ChartLoader chartLoader;

	private ReleaseMutatingTools tools;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.tools = new ReleaseMutatingTools(this.installAction, this.upgradeAction, this.uninstallAction,
				this.rollbackAction, this.testAction, this.getAction, this.chartLoader);
	}

	@Test
	void installPassesDescriptionAndLabels() {
		Chart chart = Chart.builder().metadata(ChartMetadata.builder().name("nginx").version("1.0.0").build()).build();
		when(this.chartLoader.load(any(File.class))).thenReturn(chart);
		Release release = Release.builder()
			.name("my-release")
			.namespace("default")
			.version(1)
			.chart(chart)
			.info(Release.ReleaseInfo.builder()
				.status(ReleaseStatus.DEPLOYED)
				.lastDeployed(OffsetDateTime.now())
				.build())
			.build();
		ArgumentCaptor<InstallOptions> captor = ArgumentCaptor.forClass(InstallOptions.class);
		when(this.installAction.install(captor.capture())).thenReturn(release);

		this.tools.install("/charts/nginx", "my-release", "default", false, "rollout A", Map.of("team", "payments"));

		assertEquals("rollout A", captor.getValue().getDescription());
		assertEquals("payments", captor.getValue().getLabels().get("team"));
	}

	@Test
	void installToleratesNullLabels() {
		Chart chart = Chart.builder().metadata(ChartMetadata.builder().name("nginx").version("1.0.0").build()).build();
		when(this.chartLoader.load(any(File.class))).thenReturn(chart);
		Release release = Release.builder()
			.name("my-release")
			.namespace("default")
			.version(1)
			.chart(chart)
			.info(Release.ReleaseInfo.builder()
				.status(ReleaseStatus.DEPLOYED)
				.lastDeployed(OffsetDateTime.now())
				.build())
			.build();
		ArgumentCaptor<InstallOptions> captor = ArgumentCaptor.forClass(InstallOptions.class);
		when(this.installAction.install(captor.capture())).thenReturn(release);

		this.tools.install("/charts/nginx", "my-release", "default", false, null, null);

		assertEquals(Map.of(), captor.getValue().getLabels());
	}

	@Test
	void uninstallThreadsWaitCascadeAndDryRun() {
		ArgumentCaptor<UninstallOptions> captor = ArgumentCaptor.forClass(UninstallOptions.class);

		this.tools.uninstall("my-release", "default", true, true, 90, "foreground", true);

		verify(this.uninstallAction).uninstall(captor.capture());
		UninstallOptions opts = captor.getValue();
		assertTrue(opts.isDryRun());
		assertTrue(opts.isWait());
		assertEquals(90, opts.getTimeout());
		assertEquals(CascadePolicy.FOREGROUND, opts.getCascade());
		assertTrue(opts.isKeepHistory());
	}

	@Test
	void uninstallDefaultsTimeoutWhenNull() {
		ArgumentCaptor<UninstallOptions> captor = ArgumentCaptor.forClass(UninstallOptions.class);

		this.tools.uninstall("my-release", "default", false, false, null, null, false);

		verify(this.uninstallAction).uninstall(captor.capture());
		assertEquals(300, captor.getValue().getTimeout());
		assertEquals(CascadePolicy.BACKGROUND, captor.getValue().getCascade());
	}

	@Test
	void rollbackThreadsForceCleanupAndWaitFlags() {
		Release release = Release.builder().name("my-release").namespace("default").version(2).build();
		ArgumentCaptor<RollbackOptions> captor = ArgumentCaptor.forClass(RollbackOptions.class);
		when(this.rollbackAction.rollback(captor.capture())).thenReturn(release);

		this.tools.rollback("my-release", "default", 1, false, true, true, true, true, true, 120);

		RollbackOptions opts = captor.getValue();
		assertEquals(1, opts.getRevision());
		assertTrue(opts.isForce());
		assertTrue(opts.isCleanupOnFail());
		assertTrue(opts.isRecreatePods());
		assertTrue(opts.isWait());
		assertTrue(opts.isWaitForJobs());
		assertEquals(120, opts.getTimeout());
	}

}
