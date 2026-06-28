package org.alexmond.jhelm.rest.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Gates cluster-mutating REST endpoints behind the unified {@link JhelmSecurityPolicy}.
 * An endpoint is considered mutating when its handler method is annotated with
 * {@link MutatingOperation}.
 *
 * <p>
 * For a mutating handler the decision is, in order:
 * <ul>
 * <li>if mutating operations are not enabled (deny-by-default — not {@code FULL}, or no
 * API key configured) → {@code 403 Forbidden};</li>
 * <li>otherwise if the request does not carry a valid API key → {@code 401
 * Unauthorized};</li>
 * <li>otherwise the request proceeds.</li>
 * </ul>
 * Non-mutating (read) endpoints are always allowed.
 */
@Slf4j
public class AccessModeInterceptor implements HandlerInterceptor {

	private static final String DISABLED_MESSAGE = "mutating operations are disabled — set jhelm.security.mode=FULL "
			+ "and jhelm.security.api-key to enable";

	private static final String UNAUTHORIZED_MESSAGE = "missing or invalid API key";

	private final JhelmSecurityPolicy policy;

	/**
	 * Creates the interceptor.
	 * @param policy the unified security policy that gates mutating operations
	 */
	public AccessModeInterceptor(JhelmSecurityPolicy policy) {
		this.policy = policy;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		if (handler instanceof HandlerMethod hm && hm.getMethodAnnotation(MutatingOperation.class) != null) {
			if (!this.policy.mutatingEnabled()) {
				log.info("Blocking mutating operation {} {}: mutating operations are disabled (deny-by-default)",
						request.getMethod(), request.getRequestURI());
				writeError(response, HttpStatus.FORBIDDEN, DISABLED_MESSAGE);
				return false;
			}
			if (!this.policy.validApiKey(request.getHeader(this.policy.apiKeyHeader()))) {
				log.info("Rejecting mutating operation {} {}: missing or invalid API key", request.getMethod(),
						request.getRequestURI());
				writeError(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
				return false;
			}
		}
		return true;
	}

	private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		String body = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\""
				+ message + "\",\"timestamp\":\"" + Instant.now() + "\"}";
		response.getWriter().write(body);
	}

}
