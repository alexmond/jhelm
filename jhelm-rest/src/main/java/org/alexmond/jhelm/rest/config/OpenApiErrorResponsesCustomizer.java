package org.alexmond.jhelm.rest.config;

import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Documents the shared RFC&nbsp;7807 error responses on every generated operation so the
 * OpenAPI document reflects what
 * {@link org.alexmond.jhelm.rest.JhelmRestExceptionHandler} and the API-key security
 * interceptor actually return — without annotating each endpoint by hand. Read (GET)
 * operations get {@code 400}/{@code 404}/{@code 500}; mutating operations additionally
 * get {@code 401}/{@code 403} for the security policy. Any response a controller already
 * declares (via {@code @ApiResponse}) is preserved — never overwritten.
 */
public class OpenApiErrorResponsesCustomizer implements OpenApiCustomizer {

	private static final String PROBLEM_SCHEMA = "ProblemDetail";

	private static final String PROBLEM_JSON = "application/problem+json";

	private static final String SCHEMA_REF = "#/components/schemas/" + PROBLEM_SCHEMA;

	@Override
	public void customise(OpenAPI openApi) {
		registerProblemSchema(openApi);
		if (openApi.getPaths() != null) {
			openApi.getPaths().values().forEach(this::applyToPath);
		}
	}

	private void applyToPath(PathItem pathItem) {
		for (Map.Entry<PathItem.HttpMethod, Operation> entry : pathItem.readOperationsMap().entrySet()) {
			boolean mutating = entry.getKey() != PathItem.HttpMethod.GET;
			addResponses(entry.getValue(), mutating);
		}
	}

	private void addResponses(Operation operation, boolean mutating) {
		if (operation.getResponses() == null) {
			operation.setResponses(new ApiResponses());
		}
		ApiResponses responses = operation.getResponses();
		putIfAbsent(responses, "400", "Invalid request (malformed body or failed validation).");
		if (mutating) {
			putIfAbsent(responses, "401", "Missing or invalid API key.");
			putIfAbsent(responses, "403", "Operation not permitted by the server's security mode.");
		}
		putIfAbsent(responses, "404", "The referenced chart, release, or repository does not exist.");
		putIfAbsent(responses, "500", "Unexpected server error.");
	}

	private void putIfAbsent(ApiResponses responses, String code, String description) {
		if (responses.containsKey(code)) {
			return;
		}
		Schema<?> ref = new Schema<>().$ref(SCHEMA_REF);
		ApiResponse response = new ApiResponse().description(description)
			.content(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(ref)));
		responses.addApiResponse(code, response);
	}

	private void registerProblemSchema(OpenAPI openApi) {
		Components components = openApi.getComponents();
		if (components == null) {
			components = new Components();
			openApi.setComponents(components);
		}
		if ((components.getSchemas() != null) && components.getSchemas().containsKey(PROBLEM_SCHEMA)) {
			return;
		}
		ObjectSchema problem = new ObjectSchema();
		problem.description("RFC 7807 problem detail, returned as application/problem+json.");
		problem.addProperty("type",
				new StringSchema().format("uri").description("A URI identifying the problem type."));
		problem.addProperty("title", new StringSchema().description("A short, human-readable summary of the problem."));
		problem.addProperty("status", new IntegerSchema().description("The HTTP status code."));
		problem.addProperty("detail", new StringSchema().description("A human-readable explanation of the problem."));
		problem.addProperty("instance",
				new StringSchema().format("uri").description("A URI identifying the specific occurrence."));
		problem.addProperty("timestamp",
				new StringSchema().format("date-time").description("When the error occurred (ISO-8601)."));
		components.addSchemas(PROBLEM_SCHEMA, problem);
	}

}
