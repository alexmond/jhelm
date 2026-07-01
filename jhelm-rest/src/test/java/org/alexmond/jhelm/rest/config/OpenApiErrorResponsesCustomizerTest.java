package org.alexmond.jhelm.rest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiErrorResponsesCustomizerTest {

	private OpenAPI apiWithGetAndPost() {
		Operation get = new Operation()
			.responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));
		Operation post = new Operation().responses(new ApiResponses());
		return new OpenAPI().paths(new Paths().addPathItem("/releases", new PathItem().get(get))
			.addPathItem("/releases/install", new PathItem().post(post)));
	}

	@Test
	void getOperationGetsReadErrorResponsesButNotAuth() {
		OpenAPI api = apiWithGetAndPost();
		new OpenApiErrorResponsesCustomizer().customise(api);

		ApiResponses get = api.getPaths().get("/releases").getGet().getResponses();
		assertTrue(get.containsKey("200"), "existing response preserved");
		assertTrue(get.containsKey("400"));
		assertTrue(get.containsKey("404"));
		assertTrue(get.containsKey("500"));
		assertFalse(get.containsKey("401"), "read operations are not auth-gated");
		assertFalse(get.containsKey("403"));
	}

	@Test
	void mutatingOperationAlsoGetsAuthResponses() {
		OpenAPI api = apiWithGetAndPost();
		new OpenApiErrorResponsesCustomizer().customise(api);

		ApiResponses post = api.getPaths().get("/releases/install").getPost().getResponses();
		assertTrue(post.containsKey("400"));
		assertTrue(post.containsKey("401"));
		assertTrue(post.containsKey("403"));
		assertTrue(post.containsKey("404"));
		assertTrue(post.containsKey("500"));
	}

	@Test
	void registersProblemDetailSchemaAndReferencesItAsProblemJson() {
		OpenAPI api = apiWithGetAndPost();
		new OpenApiErrorResponsesCustomizer().customise(api);

		assertNotNull(api.getComponents());
		assertTrue(api.getComponents().getSchemas().containsKey("ProblemDetail"));

		ApiResponse notFound = api.getPaths().get("/releases").getGet().getResponses().get("404");
		assertNotNull(notFound.getContent().get("application/problem+json"));
		assertTrue(
				notFound.getContent().get("application/problem+json").getSchema().get$ref().endsWith("/ProblemDetail"));
	}

}
