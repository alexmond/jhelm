package org.alexmond.gotmpl4j.spring;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * End-to-end reactive rendering, mirroring Spring Boot's
 * {@code MustacheAutoConfigurationReactiveIntegrationTests}: a controller returns a view
 * name and the autoconfigured
 * {@link org.alexmond.gotmpl4j.spring.view.GoTemplateReactiveViewResolver} renders the
 * gotmpl4j template through WebFlux. The application type is forced to reactive because
 * the test classpath also carries Spring MVC.
 */
@SpringBootTest(classes = GoTemplateReactiveWebIntegrationTest.TestApp.class,
		properties = "spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
class GoTemplateReactiveWebIntegrationTest {

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void rendersGoTemplateViewThroughWebFlux() {
		this.webTestClient.get()
			.uri("/hello")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("Hi WORLD from gotmpl4j");
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
