package org.alexmond.jhelm.rest.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.alexmond.jhelm.core.model.RepositoryConfig;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RepoController.class)
@Import(JhelmRestExceptionHandler.class)
@EnableConfigurationProperties(JhelmRestProperties.class)
class RepoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RepoManager repoManager;

	@Test
	void addRepo() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/repos").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "bitnami", "url": "https://charts.bitnami.com/bitnami"}
						"""))
			.andExpect(status().isCreated());
		verify(this.repoManager).addRepo("bitnami", "https://charts.bitnami.com/bitnami");
	}

	@Test
	void addRepoRejectsMissingName() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/repos").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"url": "https://charts.bitnami.com/bitnami"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("name is required"));
	}

	@Test
	void listRepos() throws Exception {
		RepositoryConfig config = new RepositoryConfig();
		RepositoryConfig.Repository repo = new RepositoryConfig.Repository();
		repo.setName("bitnami");
		repo.setUrl("https://charts.bitnami.com/bitnami");
		config.setRepositories(List.of(repo));
		when(this.repoManager.loadConfig()).thenReturn(config);

		this.mockMvc.perform(get("/api/v1/repos").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("bitnami"))
			.andExpect(jsonPath("$[0].url").value("https://charts.bitnami.com/bitnami"));
	}

	@Test
	void removeRepo() throws Exception {
		this.mockMvc.perform(delete("/api/v1/repos/bitnami").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent());
		verify(this.repoManager).removeRepo("bitnami");
	}

	@Test
	void updateRepo() throws Exception {
		this.mockMvc.perform(post("/api/v1/repos/bitnami/update").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
		verify(this.repoManager).updateRepo("bitnami");
	}

	@Test
	void listVersions() throws Exception {
		RepoManager.ChartVersion cv = new RepoManager.ChartVersion("nginx", "1.0.0", "1.25", null);
		when(this.repoManager.getChartVersions("bitnami", "nginx")).thenReturn(List.of(cv));

		this.mockMvc.perform(get("/api/v1/repos/bitnami/charts/nginx/versions").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].chartVersion").value("1.0.0"));
	}

	@Test
	void pullRepoChartReturnsArchiveDownload() throws Exception {
		doAnswer((invocation) -> {
			String destDir = invocation.getArgument(2);
			Path chartDir = Path.of(destDir).resolve("nginx");
			Files.createDirectories(chartDir);
			Files.writeString(chartDir.resolve("Chart.yaml"), "name: nginx\nversion: 1.0.0");
			return null;
		}).when(this.repoManager).pull(eq("bitnami/nginx"), eq("1.0.0"), anyString());

		this.mockMvc.perform(post("/api/v1/repos/pull").contentType(MediaType.APPLICATION_JSON).content("""
				{"chart": "bitnami/nginx", "version": "1.0.0"}
				"""))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/gzip"))
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"nginx-1.0.0.tgz\""));
	}

	@Test
	void pullOciChartReturnsArchiveDownload() throws Exception {
		doAnswer((invocation) -> {
			String destDir = invocation.getArgument(2);
			Path chartDir = Path.of(destDir).resolve("nginx");
			Files.createDirectories(chartDir);
			Files.writeString(chartDir.resolve("Chart.yaml"), "name: nginx\nversion: 1.0.0");
			return null;
		}).when(this.repoManager).pull(eq("oci://registry.example.com/nginx:1.0.0"), eq(null), anyString());

		this.mockMvc.perform(post("/api/v1/repos/pull").contentType(MediaType.APPLICATION_JSON).content("""
				{"chart": "oci://registry.example.com/nginx:1.0.0"}
				""")).andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/gzip"));
	}

	@Test
	void pullRejectsMissingChart() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/repos/pull").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chart is required"));
	}

	@Test
	void updateAll() throws Exception {
		this.mockMvc.perform(post("/api/v1/repos/update-all").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
		verify(this.repoManager).updateAll();
	}

	@Test
	void pushOci() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/repos/oci/push").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartTgzPath": "/tmp/nginx-1.0.0.tgz", "ociUrl": "oci://registry.example.com/nginx"}
						"""))
			.andExpect(status().isOk());
		verify(this.repoManager).pushOci("/tmp/nginx-1.0.0.tgz", "oci://registry.example.com/nginx");
	}

	@Test
	void pushOciRejectsMissingChartTgzPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/repos/oci/push").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"ociUrl": "oci://registry.example.com/nginx"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartTgzPath is required"));
	}

	@Test
	void pushOciRejectsMissingOciUrl() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/repos/oci/push").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartTgzPath": "/tmp/nginx-1.0.0.tgz"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("ociUrl is required"));
	}

}
