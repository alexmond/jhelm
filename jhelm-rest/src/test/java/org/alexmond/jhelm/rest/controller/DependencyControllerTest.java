package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartLock;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.rest.JhelmRestExceptionHandler;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DependencyController.class)
@Import(JhelmRestExceptionHandler.class)
@EnableConfigurationProperties(JhelmRestProperties.class)
class DependencyControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DependencyResolver dependencyResolver;

	@MockitoBean
	private ChartLoader chartLoader;

	@Test
	void resolveRejectsMissingChartPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/dependencies/resolve").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartPath is required"));
	}

	@Test
	void resolveReturnsYamlContent(@TempDir Path tempChart) throws Exception {
		ChartMetadata metadata = new ChartMetadata();
		metadata.setName("my-chart");
		metadata.setVersion("1.0.0");
		Chart chart = new Chart();
		chart.setMetadata(metadata);
		chart.setValues(Map.of());

		ChartLock.LockDependency dep = ChartLock.LockDependency.builder()
			.name("redis")
			.version("18.0.0")
			.repository("https://charts.bitnami.com/bitnami")
			.build();
		ChartLock lock = ChartLock.builder().dependencies(List.of(dep)).digest("sha256:abc").build();

		when(this.chartLoader.load(any(File.class))).thenReturn(chart);
		when(this.dependencyResolver.resolveDependencies(any(), anyMap(), anyList())).thenReturn(lock);

		this.mockMvc.perform(post("/api/v1/dependencies/resolve").contentType(MediaType.APPLICATION_JSON).content("""
				{"chartPath": "/tmp/my-chart"}
				"""))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "text/yaml"))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("redis")));
	}

	@Test
	void downloadRejectsMissingChartPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/dependencies/download").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartPath is required"));
	}

	@Test
	void downloadReturnsTgzArchive(@TempDir Path tempChart) throws Exception {
		// Write a Chart.lock so the controller can read it
		Files.writeString(tempChart.resolve("Chart.lock"), """
				dependencies:
				- name: redis
				  version: "18.0.0"
				  repository: "https://charts.bitnami.com/bitnami"
				digest: "sha256:abc"
				generated: "2026-03-08T00:00:00Z"
				""");

		doAnswer((invocation) -> {
			File destDir = invocation.getArgument(0);
			Path chartsDir = destDir.toPath().resolve("charts");
			Files.createDirectories(chartsDir);
			Files.writeString(chartsDir.resolve("redis-18.0.0.tgz"), "fake-dep");
			return null;
		}).when(this.dependencyResolver).downloadDependencies(any(File.class), anyList());

		this.mockMvc.perform(post("/api/v1/dependencies/download").contentType(MediaType.APPLICATION_JSON).content("""
				{"chartPath": "%s"}
				""".formatted(tempChart.toString())))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/gzip"))
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"dependencies.tgz\""));
	}

}
