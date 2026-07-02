package org.alexmond.jhelm.rest;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
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

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global error handler for the jhelm REST API. Converts exceptions into RFC&nbsp;7807
 * {@code application/problem+json} responses.
 */
@Slf4j
@RestControllerAdvice(basePackages = "org.alexmond.jhelm.rest")
public class JhelmRestExceptionHandler {

	/**
	 * Maps a missing resource to a {@code 404 Not Found} problem response.
	 * @param ex the not-found exception
	 * @return the problem detail
	 */
	@ExceptionHandler(NotFoundException.class)
	public ProblemDetail handleNotFound(NotFoundException ex) {
		log.debug("Not found: {}", ex.getMessage());
		return problem(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/**
	 * Maps Bean Validation failures on a request body to a {@code 400 Bad Request}
	 * problem response, folding the field messages into the {@code detail}.
	 * @param ex the validation exception
	 * @return the problem detail
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(FieldError::getDefaultMessage)
			.filter(Objects::nonNull)
			.distinct()
			.collect(Collectors.joining("; "));
		log.debug("Validation failed: {}", detail);
		return problem(HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Validation failed" : detail);
	}

	/**
	 * Maps invalid input to a {@code 400 Bad Request} problem response.
	 * @param ex the rejected-argument exception
	 * @return the problem detail
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
		log.debug("Bad request: {}", ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/**
	 * Maps a missing Helm release to a {@code 404 Not Found} problem response.
	 * @param ex the release-not-found exception
	 * @return the problem detail
	 */
	@ExceptionHandler(ReleaseNotFoundException.class)
	public ProblemDetail handleReleaseNotFound(ReleaseNotFoundException ex) {
		log.debug("Release not found: {}", ex.getMessage());
		return problem(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/**
	 * Maps a malformed or unverifiable chart supplied by the caller to a
	 * {@code 400 Bad Request} problem response.
	 * @param ex the chart-load or signature-verification exception
	 * @return the problem detail
	 */
	@ExceptionHandler({ ChartLoadException.class, SignatureException.class })
	public ProblemDetail handleBadChart(JhelmException ex) {
		log.debug("Bad chart input: {}", ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/**
	 * Maps a well-formed request that fails semantic processing — values violating the
	 * chart schema, or a template that cannot render — to a {@code 422 Unprocessable
	 * Entity} problem response.
	 * @param ex the schema-validation or template-render exception
	 * @return the problem detail
	 */
	@ExceptionHandler({ SchemaValidationException.class, TemplateRenderException.class })
	public ProblemDetail handleUnprocessable(JhelmException ex) {
		log.debug("Unprocessable entity: {}", ex.getMessage());
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
	}

	/**
	 * Maps a wait/readiness timeout to a {@code 504 Gateway Timeout} problem response.
	 * @param ex the wait-timeout exception
	 * @return the problem detail
	 */
	@ExceptionHandler(WaitTimeoutException.class)
	public ProblemDetail handleTimeout(WaitTimeoutException ex) {
		log.warn("Operation timed out: {}", ex.getMessage());
		return problem(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
	}

	/**
	 * Maps a failure originating in the upstream Kubernetes cluster (API call,
	 * deployment, or release storage) to a {@code 502 Bad Gateway} problem response.
	 * @param ex the cluster-side operation exception
	 * @return the problem detail
	 */
	@ExceptionHandler({ KubernetesOperationException.class, DeploymentFailedException.class,
			ReleaseStorageException.class })
	public ProblemDetail handleUpstream(JhelmException ex) {
		log.error("Upstream cluster operation failed", ex);
		return problem(HttpStatus.BAD_GATEWAY, ex.getMessage());
	}

	/**
	 * Maps any other jhelm operation failure to a {@code 500 Internal Server Error}
	 * problem response. This is the catch-all for the {@link JhelmException} hierarchy;
	 * more specific subtypes are handled above.
	 * @param ex the jhelm exception
	 * @return the problem detail
	 */
	@ExceptionHandler(JhelmException.class)
	public ProblemDetail handleJhelm(JhelmException ex) {
		log.error("jhelm operation failed", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
	}

	/**
	 * Maps any unhandled exception to a {@code 500 Internal Server Error} problem
	 * response.
	 * @param ex the unhandled exception
	 * @return the problem detail
	 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleInternalError(Exception ex) {
		log.error("Internal error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
	}

	private ProblemDetail problem(HttpStatus status, String message) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, (message != null) ? message : "Unknown error");
		problem.setProperty("timestamp", Instant.now().toString());
		return problem;
	}

}
