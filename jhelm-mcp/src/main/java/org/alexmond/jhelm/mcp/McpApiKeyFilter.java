package org.alexmond.jhelm.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires a valid {@code X-API-Key} on HTTP requests to the Spring AI MCP server
 * endpoint, returning {@code 401 Unauthorized} otherwise.
 *
 * <p>
 * <strong>When it runs:</strong> this filter is registered as a bean only when an API key
 * is configured ({@link JhelmSecurityPolicy#apiKeyConfigured()}). A read-only MCP server
 * with no API key stays open — "read-only is safe to expose" — and the local stdio
 * transport never reaches the servlet container, so the local developer path is
 * unaffected.
 *
 * <p>
 * <strong>Endpoint scope:</strong> the Spring AI
 * {@code spring-ai-starter-mcp-server-webmvc} defaults are {@code /mcp} for the
 * streamable HTTP transport and {@code /sse} + {@code /mcp/message} for the SSE
 * transport. This filter therefore guards requests whose path starts with {@code /mcp} or
 * {@code /sse}, covering both transports' default mappings. (If those endpoints are
 * remapped via {@code spring.ai.mcp.server.streamable-http.mcp-endpoint} /
 * {@code .sse-endpoint}, adjust the configured prefixes accordingly.)
 */
@Slf4j
public class McpApiKeyFilter extends OncePerRequestFilter {

	private static final String MESSAGE = "missing or invalid API key";

	private final JhelmSecurityPolicy policy;

	/**
	 * Creates the filter.
	 * @param policy the unified security policy used to validate the presented API key
	 */
	public McpApiKeyFilter(JhelmSecurityPolicy policy) {
		this.policy = policy;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !(path.startsWith("/mcp") || path.startsWith("/sse"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!this.policy.validApiKey(request.getHeader(this.policy.apiKeyHeader()))) {
			log.info("Rejecting MCP request {} {}: missing or invalid API key", request.getMethod(),
					request.getRequestURI());
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			String body = "{\"status\":" + HttpStatus.UNAUTHORIZED.value() + ",\"error\":\""
					+ HttpStatus.UNAUTHORIZED.getReasonPhrase() + "\",\"message\":\"" + MESSAGE + "\",\"timestamp\":\""
					+ Instant.now() + "\"}";
			response.getWriter().write(body);
			return;
		}
		filterChain.doFilter(request, response);
	}

}
