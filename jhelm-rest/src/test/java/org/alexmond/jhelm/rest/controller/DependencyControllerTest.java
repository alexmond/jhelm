package org.alexmond.jhelm.rest.controller;

import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.rest.JhelmRestExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DependencyController.class)
@Import(JhelmRestExceptionHandler.class)
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
	void downloadRejectsMissingChartPath() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/dependencies/download").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartPath is required"));
	}

}
