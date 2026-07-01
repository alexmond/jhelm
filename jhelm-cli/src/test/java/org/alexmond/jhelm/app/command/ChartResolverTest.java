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
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.service.SignatureService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

	private File packageChart() {
		PackageAction packageAction = new PackageAction(chartLoader, signatureService);
		packageAction.setDestination(tempDir.toFile());
		return packageAction.packageChart(chartDir.getAbsolutePath());
	}

}
