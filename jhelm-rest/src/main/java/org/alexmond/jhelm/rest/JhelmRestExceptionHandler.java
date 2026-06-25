package org.alexmond.jhelm.rest;

import java.time.Instant;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global error handler for the jhelm REST API. Converts exceptions into structured JSON
 * error responses.
 */
@Slf4j
@RestControllerAdvice(basePackages = "org.alexmond.jhelm.rest")
public class JhelmRestExceptionHandler {

	/**
	 * Maps invalid input to a {@code 400 Bad Request} JSON error response.
	 * @param ex the rejected-argument exception
	 * @return the structured error response
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
		log.debug("Bad request: {}", ex.getMessage());
		return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/**
	 * Maps any unhandled exception to a {@code 500 Internal Server Error} JSON error
	 * response.
	 * @param ex the unhandled exception
	 * @return the structured error response
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleInternalError(Exception ex) {
		log.error("Internal error", ex);
		return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
	}

	private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
		Map<String, Object> body = Map.of("status", status.value(), "error", status.getReasonPhrase(), "message",
				(message != null) ? message : "Unknown error", "timestamp", Instant.now().toString());
		return ResponseEntity.status(status).body(body);
	}

}
