package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.PackageAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.VerifyAction;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
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
	private PackageAction packageAction;

	@MockitoBean
	private VerifyAction verifyAction;

	@MockitoBean
	private ShowAction showAction;

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
	void packageChartReturnsArchiveDownload(@TempDir Path tempChart) throws Exception {
		Files.writeString(tempChart.resolve("Chart.yaml"), "name: nginx\nversion: 1.0.0");
		File archive = tempChart.resolve("nginx-1.0.0.tgz").toFile();
		Files.writeString(archive.toPath(), "fake-tgz-content");

		when(this.packageAction.packageChart(eq("/tmp/nginx"))).thenReturn(archive);

		this.mockMvc.perform(post("/api/v1/charts/package").contentType(MediaType.APPLICATION_JSON).content("""
				{"chartPath": "/tmp/nginx"}
				"""))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/gzip"))
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"nginx-1.0.0.tgz\""));
	}

	@Test
	void packageRejectsMissingChartPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/package").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartPath is required"));
	}

	@Test
	void verifySucceeds() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/verify").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartTgzPath": "/tmp/nginx-1.0.0.tgz", "keyringPath": "/tmp/keyring.gpg"}
						"""))
			.andExpect(status().isOk());
		verify(this.verifyAction).verify("/tmp/nginx-1.0.0.tgz", "/tmp/keyring.gpg");
	}

	@Test
	void verifyRejectsMissingChartTgzPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/verify").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"keyringPath": "/tmp/keyring.gpg"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartTgzPath is required"));
	}

	@Test
	void verifyRejectsMissingKeyringPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/verify").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartTgzPath": "/tmp/nginx-1.0.0.tgz"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("keyringPath is required"));
	}

	@Test
	void showAllReturnsChartInfo() throws Exception {
		when(this.showAction.showAll("/tmp/nginx")).thenReturn("# Chart.yaml\nname: nginx");

		this.mockMvc
			.perform(get("/api/v1/charts/show").param("chartPath", "/tmp/nginx").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("# Chart.yaml\nname: nginx"));
	}

	@Test
	void showValuesReturnsValues() throws Exception {
		when(this.showAction.showValues("/tmp/nginx")).thenReturn("replicas: 1");

		this.mockMvc
			.perform(get("/api/v1/charts/show/values").param("chartPath", "/tmp/nginx")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("replicas: 1"));
	}

	@Test
	void showReadmeReturnsReadme() throws Exception {
		when(this.showAction.showReadme("/tmp/nginx")).thenReturn("# Nginx Chart");

		this.mockMvc
			.perform(get("/api/v1/charts/show/readme").param("chartPath", "/tmp/nginx")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("# Nginx Chart"));
	}

	@Test
	void showChartReturnsMetadata() throws Exception {
		when(this.showAction.showChart("/tmp/nginx")).thenReturn("apiVersion: v2\nname: nginx");

		this.mockMvc
			.perform(get("/api/v1/charts/show/chart").param("chartPath", "/tmp/nginx")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("apiVersion: v2\nname: nginx"));
	}

	@Test
	void showCrdsReturnsCrds() throws Exception {
		when(this.showAction.showCrds("/tmp/nginx")).thenReturn("apiVersion: apiextensions.k8s.io/v1\nkind: CRD");

		this.mockMvc
			.perform(
					get("/api/v1/charts/show/crds").param("chartPath", "/tmp/nginx").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string("apiVersion: apiextensions.k8s.io/v1\nkind: CRD"));
	}

}
