package org.alexmond.jhelm.rest.controller;

import java.util.List;

import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.rest.JhelmRestExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RepoController.class)
@Import(JhelmRestExceptionHandler.class)
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
	void pullChart() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/repos/bitnami/charts/nginx/pull").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"version": "1.0.0", "destination": "/tmp/charts"}
						"""))
			.andExpect(status().isOk());
		verify(this.repoManager).pull("nginx", "bitnami", "1.0.0", "/tmp/charts");
	}

}
