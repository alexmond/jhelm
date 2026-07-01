package org.alexmond.jhelm.rest;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

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
