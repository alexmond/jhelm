package org.alexmond.jhelm.rest.controller;

import java.util.List;

import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.rest.JhelmRestExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HubController.class)
@Import(JhelmRestExceptionHandler.class)
class HubControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SearchHubAction searchHubAction;

	@Test
	void searchReturnsResults() throws Exception {
		SearchHubAction.HubResult result = new SearchHubAction.HubResult();
		result.setName("nginx");
		result.setVersion("1.0.0");
		result.setRepoName("bitnami");
		result.setRepoUrl("https://charts.bitnami.com/bitnami");
		when(this.searchHubAction.search("nginx", 20)).thenReturn(List.of(result));

		this.mockMvc.perform(get("/api/v1/hub/search").param("keyword", "nginx").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("nginx"))
			.andExpect(jsonPath("$[0].version").value("1.0.0"));
	}

	@Test
	void searchWithCustomMaxResults() throws Exception {
		when(this.searchHubAction.search("redis", 5)).thenReturn(List.of());

		this.mockMvc
			.perform(get("/api/v1/hub/search").param("keyword", "redis")
				.param("maxResults", "5")
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

}
