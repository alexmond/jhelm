package org.alexmond.jhelm.rest.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartLock;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.rest.JhelmRestExceptionHandler;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

	@MockitoBean
	private RepoManager repoManager;

	@Test
	void resolveRejectsMissingChartRef() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/dependencies/resolve").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartRef is required"));
	}

	@Test
	void resolveWithChartRefReturnsYaml() throws Exception {
		doAnswer((invocation) -> {
			String destDir = invocation.getArgument(2);
			Path chartDir = Path.of(destDir).resolve("nginx");
			Files.createDirectories(chartDir);
			Files.writeString(chartDir.resolve("Chart.yaml"), "name: nginx\nversion: 18.3.1");
			return null;
		}).when(this.repoManager).pull(eq("bitnami/nginx"), eq("18.3.1"), anyString());

		ChartMetadata metadata = new ChartMetadata();
		metadata.setName("nginx");
		metadata.setVersion("18.3.1");
		Chart chart = new Chart();
		chart.setMetadata(metadata);
		chart.setValues(Map.of());

		ChartLock.LockDependency dep = ChartLock.LockDependency.builder()
			.name("redis")
			.version("18.0.0")
			.repository("https://charts.bitnami.com/bitnami")
			.build();
		ChartLock lock = ChartLock.builder().dependencies(List.of(dep)).digest("sha256:abc").build();

		when(this.chartLoader.load(any(java.io.File.class))).thenReturn(chart);
		when(this.dependencyResolver.resolveDependencies(any(), anyMap(), anyList())).thenReturn(lock);

		this.mockMvc.perform(post("/api/v1/dependencies/resolve").contentType(MediaType.APPLICATION_JSON).content("""
				{"chartRef": "bitnami/nginx", "version": "18.3.1"}
				"""))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "text/yaml"))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("redis")));
	}

}
