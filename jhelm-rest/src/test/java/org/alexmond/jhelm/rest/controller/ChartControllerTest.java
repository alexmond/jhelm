package org.alexmond.jhelm.rest.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChartController.class)
@Import(JhelmRestExceptionHandler.class)
@EnableConfigurationProperties(JhelmRestProperties.class)
class ChartControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TemplateAction templateAction;

	@MockitoBean
	private LintAction lintAction;

	@MockitoBean
	private CreateAction createAction;

	@MockitoBean
	private ShowAction showAction;

	@MockitoBean
	private RepoManager repoManager;

	@Test
	void templateRendersManifest() throws Exception {
		when(this.templateAction.render(eq("/tmp/nginx"), eq("my-release"), eq("default"), anyMap()))
			.thenReturn("apiVersion: v1\nkind: ConfigMap");

		this.mockMvc
			.perform(post("/api/v1/charts/template").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartPath": "/tmp/nginx", "releaseName": "my-release"}
						"""))
			.andExpect(status().isOk())
			.andExpect(content().string("apiVersion: v1\nkind: ConfigMap"));
	}

	@Test
	void templateRejectsMissingChartPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/template").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"releaseName": "my-release"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartPath is required"));
	}

	@Test
	void lintReturnsResult() throws Exception {
		LintAction.LintResult result = new LintAction.LintResult("/tmp/nginx", List.of(), List.of("missing desc"));
		when(this.lintAction.lint(eq("/tmp/nginx"), anyMap(), eq(false))).thenReturn(result);

		this.mockMvc
			.perform(post("/api/v1/charts/lint").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartPath": "/tmp/nginx"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ok").value(true))
			.andExpect(jsonPath("$.warnings[0]").value("missing desc"));
	}

	@Test
	void lintRejectsMissingChartPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/lint").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartPath is required"));
	}

	@Test
	void createReturnsArchiveDownload() throws Exception {
		doAnswer((invocation) -> {
			Path chartPath = invocation.getArgument(0);
			Files.createDirectories(chartPath);
			Files.writeString(chartPath.resolve("Chart.yaml"), "name: my-chart\nversion: 0.1.0");
			return null;
		}).when(this.createAction).create(any(Path.class));

		this.mockMvc.perform(post("/api/v1/charts/create").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "my-chart"}
				"""))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/gzip"))
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"my-chart.tgz\""));
	}

	@Test
	void createRejectsMissingName() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/create").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("name is required"));
	}

	@Test
	void showAllReturnsChartInfo() throws Exception {
		stubPull();
		when(this.showAction.showAll(anyString())).thenReturn("# Chart.yaml\nname: nginx");

		this.mockMvc
			.perform(get("/api/v1/charts/show").param("chartRef", "bitnami/nginx")
				.param("version", "18.3.1")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("# Chart.yaml\nname: nginx"));
	}

	@Test
	void showValuesReturnsValues() throws Exception {
		stubPull();
		when(this.showAction.showValues(anyString())).thenReturn("replicas: 1");

		this.mockMvc
			.perform(get("/api/v1/charts/show/values").param("chartRef", "bitnami/nginx")
				.param("version", "18.3.1")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("replicas: 1"));
	}

	@Test
	void showReadmeReturnsReadme() throws Exception {
		stubPull();
		when(this.showAction.showReadme(anyString())).thenReturn("# Nginx Chart");

		this.mockMvc
			.perform(get("/api/v1/charts/show/readme").param("chartRef", "bitnami/nginx")
				.param("version", "18.3.1")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("# Nginx Chart"));
	}

	@Test
	void showChartReturnsMetadata() throws Exception {
		stubPull();
		when(this.showAction.showChart(anyString())).thenReturn("apiVersion: v2\nname: nginx");

		this.mockMvc
			.perform(get("/api/v1/charts/show/chart").param("chartRef", "bitnami/nginx")
				.param("version", "18.3.1")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("apiVersion: v2\nname: nginx"));
	}

	@Test
	void showCrdsReturnsCrds() throws Exception {
		stubPull();
		when(this.showAction.showCrds(anyString())).thenReturn("apiVersion: apiextensions.k8s.io/v1\nkind: CRD");

		this.mockMvc
			.perform(get("/api/v1/charts/show/crds").param("chartRef", "bitnami/nginx")
				.param("version", "18.3.1")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("apiVersion: apiextensions.k8s.io/v1\nkind: CRD"));
	}

	@Test
	void showWithoutVersionPulls() throws Exception {
		stubPull();
		when(this.showAction.showAll(anyString())).thenReturn("name: nginx");

		this.mockMvc
			.perform(get("/api/v1/charts/show").param("chartRef", "bitnami/nginx").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("name: nginx"));
	}

	private void stubPull() throws Exception {
		doAnswer((invocation) -> {
			String destDir = invocation.getArgument(2);
			Path chartDir = Path.of(destDir).resolve("nginx");
			Files.createDirectories(chartDir);
			Files.writeString(chartDir.resolve("Chart.yaml"), "name: nginx\nversion: 18.3.1");
			return null;
		}).when(this.repoManager).pull(anyString(), any(), anyString());
	}

}
