package org.alexmond.jhelm.rest.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects cluster-mutating endpoints when the REST API is in
 * {@link JhelmAccessMode#READ_ONLY READ_ONLY} mode. An endpoint is considered mutating
 * when its handler method is annotated with {@link MutatingOperation}.
 */
@Slf4j
public class AccessModeInterceptor implements HandlerInterceptor {

	private static final String MESSAGE = "Operation not permitted: the jhelm REST API is in READ_ONLY mode";

	private final JhelmRestProperties properties;

	/**
	 * Creates the interceptor.
	 * @param properties the REST module configuration carrying the access mode
	 */
	public AccessModeInterceptor(JhelmRestProperties properties) {
		this.properties = properties;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		if (handler instanceof HandlerMethod hm && hm.getMethodAnnotation(MutatingOperation.class) != null
				&& this.properties.getMode() == JhelmAccessMode.READ_ONLY) {
			log.info("Blocking mutating operation {} {}: jhelm REST API is in READ_ONLY mode", request.getMethod(),
					request.getRequestURI());
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			String body = "{\"status\":" + HttpStatus.FORBIDDEN.value() + ",\"error\":\""
					+ HttpStatus.FORBIDDEN.getReasonPhrase() + "\",\"message\":\"" + MESSAGE + "\",\"timestamp\":\""
					+ Instant.now() + "\"}";
			response.getWriter().write(body);
			return false;
		}
		return true;
	}

}
