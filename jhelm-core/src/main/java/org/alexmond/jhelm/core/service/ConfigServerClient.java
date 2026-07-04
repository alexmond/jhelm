package org.alexmond.jhelm.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.model.Environment;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpEntity;

import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches the structured Environment JSON from a Spring Cloud Config Server ({@code GET
 * {uri}/{application}/{profiles}[/{label}]}).
 * <p>
 * The fetch rides the shared SSRF-guarded HTTP path ({@link RepoHttpClientFactory} from
 * {@link RepoManager}): the URL is validated against the {@code block-private-networks}
 * policy (#630), credentials are host-gated, and TLS follows the global
 * {@code insecure-skip-tls-verify} setting — the config-server URI is user/operator
 * supplied, so it must not use the unguarded {@code java.net.http} path.
 */
@Slf4j
public class ConfigServerClient {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private final RepoManager repoManager;

	/**
	 * @param repoManager provides the shared SSRF-guarded HTTP client factory
	 */
	public ConfigServerClient(RepoManager repoManager) {
		this.repoManager = repoManager;
	}

	/**
	 * Fetch and parse the Environment for the given request.
	 * @param request the resolved config-server request
	 * @return the parsed {@link Environment}
	 * @throws IOException on connection failure, a non-2xx response, or a parse error
	 */
	public Environment fetch(ConfigServerRequest request) throws IOException {
		String url = buildUrl(request);
		HttpGet get = new HttpGet(url);
		get.setHeader("User-Agent", "jhelm");

		RepositoryConfig.Repository repo = authRepository(request, url, get);

		if (log.isInfoEnabled()) {
			log.info("Fetching values from config server: {}", url);
		}
		return repoManager.httpClientFactory().executeGet(get, repo, (response) -> {
			int code = response.getCode();
			if (code == 401 || code == 403) {
				throw new IOException("Config server authentication failed (" + code + " " + response.getReasonPhrase()
						+ ") for " + url);
			}
			if (code < 200 || code >= 300) {
				throw new IOException(
						"Config server returned " + code + " " + response.getReasonPhrase() + " for " + url);
			}
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				return new Environment();
			}
			try (InputStream in = entity.getContent()) {
				byte[] body = in.readAllBytes();
				return JSON_MAPPER.readValue(new String(body, StandardCharsets.UTF_8), Environment.class);
			}
		});
	}

	/**
	 * Applies auth to the request and returns the {@link RepositoryConfig.Repository}
	 * passed to {@code executeGet} (its URL host-gates basic-auth credentials). A bearer
	 * token, when present, is set directly and takes precedence over basic auth.
	 */
	private RepositoryConfig.Repository authRepository(ConfigServerRequest request, String url, HttpGet get) {
		if (request.token() != null && !request.token().isBlank()) {
			get.setHeader("Authorization", "Bearer " + request.token());
			// No username on the repo, so RepoHttpClientFactory won't overwrite the
			// header.
			return RepositoryConfig.Repository.builder().url(url).build();
		}
		return RepositoryConfig.Repository.builder()
			.url(url)
			.username(request.username())
			.password(request.password())
			.build();
	}

	static String buildUrl(ConfigServerRequest request) {
		String base = request.uri();
		if (base == null || base.isBlank()) {
			throw new IllegalArgumentException("config server URI is required");
		}
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		List<String> profiles = request.profiles();
		String profileSegment = (profiles == null || profiles.isEmpty()) ? "default" : String.join(",", profiles);
		StringBuilder url = new StringBuilder(base).append('/')
			.append(request.application())
			.append('/')
			.append(profileSegment);
		if (request.label() != null && !request.label().isBlank()) {
			url.append('/').append(request.label());
		}
		return url.toString();
	}

}
