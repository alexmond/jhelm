package org.alexmond.gotmpl4j.spring;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end servlet rendering, mirroring Spring Boot's
 * {@code MustacheAutoConfigurationServletIntegrationTests}: a controller returns a view
 * name, the autoconfigured
 * {@link org.alexmond.gotmpl4j.spring.view.GoTemplateViewResolver} resolves it, and the
 * gotmpl4j template is rendered to the response.
 */
@SpringBootTest(classes = GoTemplateServletWebIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
class GoTemplateServletWebIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rendersGoTemplateViewThroughMvc() throws Exception {
		this.mockMvc.perform(get("/hello"))
			.andExpect(status().isOk())
			.andExpect(content().string("Hi WORLD from gotmpl4j"));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestApp {

		@Bean
		HelloController helloController() {
			return new HelloController();
		}

	}

	@Controller
	static class HelloController {

		@GetMapping("/hello")
		String hello(Model model) {
			model.addAttribute("Name", "world");
			model.addAttribute("Site", "gotmpl4j");
			return "hello";
		}

	}

}
