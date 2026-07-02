package org.alexmond.jhelm.rest;

import java.lang.reflect.Method;
import java.util.List;

import org.alexmond.jhelm.core.exception.ChartLoadException;
import org.alexmond.jhelm.core.exception.DeploymentFailedException;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.exception.ReleaseStorageException;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import org.alexmond.jhelm.core.exception.SignatureException;
import org.alexmond.jhelm.core.exception.TemplateRenderException;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;
import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JhelmRestExceptionHandler}, exercising every handler and the
 * {@code problem()} helper's edge branches directly (the controller {@code @WebMvcTest}
 * classes cover the happy 404/400 paths through MVC; this pins the 500 path and the
 * null-message / no-field-error fallbacks that MVC doesn't reach).
 */
class JhelmRestExceptionHandlerTest {

	private final JhelmRestExceptionHandler handler = new JhelmRestExceptionHandler();

	@Test
	void notFoundMapsTo404WithDetailAndTimestamp() {
		ProblemDetail problem = this.handler.handleNotFound(new NotFoundException("Release 'x' not found"));

		assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
		assertEquals("Release 'x' not found", problem.getDetail());
		assertNotNull(problem.getProperties());
		assertNotNull(problem.getProperties().get("timestamp"));
	}

	@Test
	void illegalArgumentMapsTo400() {
		ProblemDetail problem = this.handler.handleBadRequest(new IllegalArgumentException("bad input"));

		assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
		assertEquals("bad input", problem.getDetail());
	}

	@Test
	void validationFoldsFieldMessagesIntoDetail() throws Exception {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
		binding.addError(new FieldError("request", "chartRef", "chartRef is required"));
		binding.addError(new FieldError("request", "name", "name is required"));

		ProblemDetail problem = this.handler.handleValidation(methodArgNotValid(binding));

		assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
		assertEquals("chartRef is required; name is required", problem.getDetail());
	}

	@Test
	void validationWithNoFieldErrorsFallsBackToGenericMessage() throws Exception {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");

		ProblemDetail problem = this.handler.handleValidation(methodArgNotValid(binding));

		assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
		assertEquals("Validation failed", problem.getDetail());
	}

	@Test
	void unhandledExceptionMapsTo500() {
		ProblemDetail problem = this.handler.handleInternalError(new RuntimeException("boom"));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
		assertEquals("boom", problem.getDetail());
	}

	@Test
	void nullMessageFallsBackToUnknownError() {
		ProblemDetail problem = this.handler.handleInternalError(new RuntimeException());

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
		assertEquals("Unknown error", problem.getDetail());
		assertTrue(problem.getProperties().containsKey("timestamp"));
	}

	@Test
	void releaseNotFoundMapsTo404() {
		ProblemDetail problem = this.handler.handleReleaseNotFound(new ReleaseNotFoundException("no release 'x'"));

		assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
		assertEquals("no release 'x'", problem.getDetail());
	}

	@Test
	void malformedChartMapsTo400() {
		ProblemDetail chartLoad = this.handler
			.handleBadChart(new ChartLoadException("bad chart", "/tmp/c", "check Chart.yaml"));
		ProblemDetail signature = this.handler.handleBadChart(new SignatureException("provenance failed"));

		assertEquals(HttpStatus.BAD_REQUEST.value(), chartLoad.getStatus());
		assertEquals(HttpStatus.BAD_REQUEST.value(), signature.getStatus());
	}

	@Test
	void schemaAndTemplateFailuresMapTo422() {
		ProblemDetail schema = this.handler
			.handleUnprocessable(new SchemaValidationException("mychart", List.of("values.replicas: must be >= 0")));
		ProblemDetail template = this.handler
			.handleUnprocessable(new TemplateRenderException("render failed", "mychart", "deployment.yaml"));

		assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), schema.getStatus());
		assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), template.getStatus());
	}

	@Test
	void waitTimeoutMapsTo504() {
		ProblemDetail problem = this.handler
			.handleTimeout(new WaitTimeoutException("timed out waiting for readiness", List.of()));

		assertEquals(HttpStatus.GATEWAY_TIMEOUT.value(), problem.getStatus());
	}

	@Test
	void upstreamClusterFailuresMapTo502() {
		ProblemDetail kube = this.handler.handleUpstream(new KubernetesOperationException("apply failed"));
		ProblemDetail deploy = this.handler
			.handleUpstream(new DeploymentFailedException("deploy failed", new RuntimeException("api"), "manifest"));
		ProblemDetail storage = this.handler.handleUpstream(new ReleaseStorageException("secret write failed"));

		assertEquals(HttpStatus.BAD_GATEWAY.value(), kube.getStatus());
		assertEquals(HttpStatus.BAD_GATEWAY.value(), deploy.getStatus());
		assertEquals(HttpStatus.BAD_GATEWAY.value(), storage.getStatus());
	}

	@Test
	void otherJhelmExceptionMapsTo500() {
		ProblemDetail problem = this.handler.handleJhelm(new JhelmException("generic jhelm failure"));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
		assertEquals("generic jhelm failure", problem.getDetail());
	}

	private MethodArgumentNotValidException methodArgNotValid(BeanPropertyBindingResult binding) throws Exception {
		Method method = Target.class.getDeclaredMethod("handle", String.class);
		MethodParameter parameter = new MethodParameter(method, 0);
		return new MethodArgumentNotValidException(parameter, binding);
	}

	/** Reflection target providing a real {@link MethodParameter} for the test. */
	private static final class Target {

		void handle(String body) {
		}

	}

}
