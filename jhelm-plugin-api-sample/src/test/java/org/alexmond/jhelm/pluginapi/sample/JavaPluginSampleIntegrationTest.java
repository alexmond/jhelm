package org.alexmond.jhelm.pluginapi.sample;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.JhelmChartDownloaderAdapter;
import org.alexmond.jhelm.core.service.JhelmLifecycleListenerAdapter;
import org.alexmond.jhelm.core.service.JhelmPostRendererAdapter;
import org.alexmond.jhelm.core.service.JhelmTemplateFunctionAdapter;
import org.alexmond.jhelm.core.service.LifecycleListener;
import org.alexmond.jhelm.core.service.LifecyclePhase;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.pluginapi.JhelmChartDownloader;
import org.alexmond.jhelm.pluginapi.JhelmLifecycleListener;
import org.alexmond.jhelm.pluginapi.JhelmPlugins;
import org.alexmond.jhelm.pluginapi.JhelmPostRenderer;
import org.alexmond.jhelm.pluginapi.JhelmTemplateFunctionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that the four sample plugins are discovered via {@code ServiceLoader}
 * and work through jhelm-core's adapters, engine, and fetch path.
 */
class JavaPluginSampleIntegrationTest {

	@TempDir
	Path work;

	@Test
	void postRendererIsDiscoveredAndTransformsTheManifest() throws Exception {
		JhelmPostRenderer plugin = JhelmPlugins.fromServiceLoader(JhelmPostRenderer.class)
			.stream()
			.filter((p) -> p instanceof BannerPostRenderer)
			.findFirst()
			.orElseThrow();
		PostRenderProcessor processor = new JhelmPostRendererAdapter(plugin);
		assertTrue(processor.process("kind: ConfigMap").startsWith("# rendered via the jhelm sample post-renderer"));
	}

	@Test
	void chartDownloaderIsDiscoveredAndFetchesViaRepoManager() throws Exception {
		Path tgz = this.work.resolve("mychart-1.0.0.tgz");
		Files.write(tgz, chartArchive());
		JhelmChartDownloader plugin = JhelmPlugins.fromServiceLoader(JhelmChartDownloader.class)
			.stream()
			.filter((p) -> p instanceof FileChartDownloader)
			.findFirst()
			.orElseThrow();
		RepoManager repoManager = new RepoManager();
		repoManager.setChartDownloaders(List.of(new JhelmChartDownloaderAdapter(plugin)));
		Path dest = Files.createDirectories(this.work.resolve("out"));

		repoManager.pullFromUrl("samplefile://" + tgz, dest.toString(), "mychart-1.0.0.tgz");

		assertTrue(Files.exists(dest.resolve("mychart/Chart.yaml")));
	}

	@Test
	void lifecycleListenerIsDiscoveredAndReceivesEvents() {
		JhelmLifecycleListener plugin = JhelmPlugins.fromServiceLoader(JhelmLifecycleListener.class)
			.stream()
			.filter((p) -> p instanceof LoggingLifecycleListener)
			.findFirst()
			.orElseThrow();
		LifecycleListener adapter = new JhelmLifecycleListenerAdapter(plugin);
		assertDoesNotThrow(() -> adapter.onEvent(LifecyclePhase.POST_INSTALL, "rel", "default", Map.of()));
	}

	@Test
	void templateFunctionIsDiscoveredAndCallableFromAChart() {
		List<JhelmTemplateFunctionProvider> providers = JhelmPlugins
			.fromServiceLoader(JhelmTemplateFunctionProvider.class);
		Engine engine = new Engine();
		engine.setPluginFunctions(JhelmTemplateFunctionAdapter.collect(providers));
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder().name("mychart").version("1.0.0").build())
			.templates(List
				.of(Chart.Template.builder().name("cm.yaml").data("greeting: {{ sample_greet \"world\" }}").build()))
			.values(Map.of())
			.build();

		String result = engine.render(chart, Map.of(),
				ReleaseContext.builder().name("rel").namespace("default").install(true).revision(1).build());

		assertTrue(result.contains("greeting: hello, world"), result);
	}

	private static byte[] chartArchive() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (OutputStream gz = new GZIPOutputStream(bytes);
				TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
			byte[] content = "apiVersion: v2\nname: mychart\nversion: 1.0.0\n".getBytes(StandardCharsets.UTF_8);
			TarArchiveEntry entry = new TarArchiveEntry("mychart/Chart.yaml");
			entry.setSize(content.length);
			tar.putArchiveEntry(entry);
			tar.write(content);
			tar.closeArchiveEntry();
		}
		return bytes.toByteArray();
	}

}
