package org.alexmond.jhelm.app.command;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.alexmond.jhelm.core.action.PackageAction;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.service.SignatureService;
import org.alexmond.jhelm.core.util.ValuesProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChartResolverTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final SignatureService signatureService = new SignatureService();

	private final ChartResolver resolver = new ChartResolver(chartLoader, new RepoManager(),
			new VerifyAction(signatureService));

	@TempDir
	Path tempDir;

	private File chartDir;

	@BeforeEach
	void setUp() throws Exception {
		chartDir = tempDir.resolve("test-chart").toFile();
		chartDir.mkdirs();
		Files.writeString(chartDir.toPath().resolve("Chart.yaml"), """
				apiVersion: v2
				name: test-chart
				version: 1.0.0
				""");
	}

	@Test
	void resolvesChartDirectory() throws Exception {
		Chart chart = resolver.resolve(chartDir.getAbsolutePath(), false, null);
		assertEquals("test-chart", chart.getMetadata().getName());
	}

	@Test
	void resolvesPackagedTgz() throws Exception {
		File tgz = packageChart();
		Chart chart = resolver.resolve(tgz.getAbsolutePath(), false, null);
		assertEquals("test-chart", chart.getMetadata().getName());
	}

	@Test
	void verifyOnDirectoryIsRejected() {
		// a directory has no provenance file, so --verify cannot apply
		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(chartDir.getAbsolutePath(), true, null));
	}

	@Test
	void verifyAbortsWhenProvenanceMissing() throws Exception {
		// an unsigned .tgz has no .prov, so verification aborts before the chart is
		// loaded
		File tgz = packageChart();
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(tgz.getAbsolutePath(), true, tempDir.resolve("keyring.gpg").toString()));
	}

	@Test
	void missingPathIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(tempDir.resolve("does-not-exist").toString(), false, null));
	}

	@Test
	void resolveFromRepoWithRepoUrlPullsAndLoadsChart() throws Exception {
		RepoManager repoManager = mock(RepoManager.class);
		ChartResolver repoResolver = new ChartResolver(chartLoader, repoManager, new VerifyAction(signatureService));
		// The --repo pull writes a .tgz into the destination directory (arg 3).
		doAnswer((inv) -> {
			Files.writeString(Path.of(inv.getArgument(3), "nginx-1.2.3.tgz"), "fake-archive");
			return null;
		}).when(repoManager)
			.pullFromRepoUrl(eq("https://charts.example.com"), eq("nginx"), eq("1.2.3"), anyString(), any());
		stubUntar(repoManager, "nginx");

		Chart chart = repoResolver.resolveFromRepo("nginx", "1.2.3", "https://charts.example.com",
				RepositoryConfig.Repository.builder().build(), false, null, ValuesProfiles.none());

		assertEquals("nginx", chart.getMetadata().getName());
		verify(repoManager).pullFromRepoUrl(eq("https://charts.example.com"), eq("nginx"), eq("1.2.3"), anyString(),
				any());
	}

	@Test
	void resolveFromRepoWithChartRefPullsFromRegisteredRepo() throws Exception {
		RepoManager repoManager = mock(RepoManager.class);
		ChartResolver repoResolver = new ChartResolver(chartLoader, repoManager, new VerifyAction(signatureService));
		doAnswer((inv) -> {
			Files.writeString(Path.of(inv.getArgument(2), "redis-17.0.0.tgz"), "fake-archive");
			return null;
		}).when(repoManager).pull(eq("bitnami/redis"), eq("17.0.0"), anyString());
		stubUntar(repoManager, "redis");

		Chart chart = repoResolver.resolveFromRepo("bitnami/redis", "17.0.0", null, null, false, null,
				ValuesProfiles.none());

		assertEquals("redis", chart.getMetadata().getName());
		verify(repoManager).pull(eq("bitnami/redis"), eq("17.0.0"), anyString());
	}

	@Test
	void resolveFromRepoFallsBackToLocalPathWithoutPulling() throws Exception {
		RepoManager repoManager = mock(RepoManager.class);
		ChartResolver localResolver = new ChartResolver(chartLoader, repoManager, new VerifyAction(signatureService));

		Chart chart = localResolver.resolveFromRepo(chartDir.getAbsolutePath(), null, null, null, false, null,
				ValuesProfiles.none());

		assertEquals("test-chart", chart.getMetadata().getName());
		verify(repoManager, never()).pull(anyString(), anyString(), anyString());
	}

	// Makes the mocked RepoManager.untar lay down a loadable chart directory under the
	// work dir.
	private void stubUntar(RepoManager repoManager, String chartName) throws Exception {
		doAnswer((inv) -> {
			File workDir = inv.getArgument(1);
			File cd = new File(workDir, chartName);
			cd.mkdirs();
			Files.writeString(cd.toPath().resolve("Chart.yaml"),
					"apiVersion: v2\nname: " + chartName + "\nversion: 1.0.0\n");
			return null;
		}).when(repoManager).untar(any(File.class), any(File.class));
	}

	private File packageChart() {
		PackageAction packageAction = new PackageAction(chartLoader, signatureService);
		packageAction.setDestination(tempDir.toFile());
		return packageAction.packageChart(chartDir.getAbsolutePath());
	}

}
