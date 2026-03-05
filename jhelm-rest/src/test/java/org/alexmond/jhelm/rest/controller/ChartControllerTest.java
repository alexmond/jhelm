package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.PackageAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.rest.JhelmRestExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChartController.class)
@Import(JhelmRestExceptionHandler.class)
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
	void createScaffoldsChart() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/create").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartPath": "/tmp/my-chart"}
						"""))
			.andExpect(status().isCreated());
		verify(this.createAction).create(Path.of("/tmp/my-chart"));
	}

	@Test
	void createRejectsMissingChartPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/charts/create").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartPath is required"));
	}

	@Test
	void packageChartReturnsArchivePath() throws Exception {
		when(this.packageAction.packageChart("/tmp/nginx")).thenReturn(new File("/tmp/nginx-1.0.0.tgz"));

		this.mockMvc
			.perform(post("/api/v1/charts/package").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartPath": "/tmp/nginx"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archivePath").value("/tmp/nginx-1.0.0.tgz"));
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

}
