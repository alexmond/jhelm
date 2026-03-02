package org.alexmond.jhelm.core.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import org.alexmond.jhelm.core.model.RepositoryConfig;

class RepoHttpClientFactoryTest {

	@Test
	void testConfigureAuthSetsBasicHeader() {
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		RepoHttpClientFactory factory = new RepoHttpClientFactory(mockClient, false);
		RepositoryConfig.Repository repo = RepositoryConfig.Repository.builder()
			.name("test")
			.url("https://example.com")
			.username("myuser")
			.password("mypass")
			.build();
		HttpGet request = new HttpGet("https://example.com/index.yaml");
		factory.configureAuth(request, repo);
		String expected = "Basic "
				+ Base64.getEncoder().encodeToString("myuser:mypass".getBytes(StandardCharsets.UTF_8));
		assertEquals(expected, request.getFirstHeader("Authorization").getValue());
	}

	@Test
	void testConfigureAuthSkipsWhenNoCredentials() {
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		RepoHttpClientFactory factory = new RepoHttpClientFactory(mockClient, false);
		RepositoryConfig.Repository repo = RepositoryConfig.Repository.builder()
			.name("test")
			.url("https://example.com")
			.build();
		HttpGet request = new HttpGet("https://example.com/index.yaml");
		factory.configureAuth(request, repo);
		assertNull(request.getFirstHeader("Authorization"));
	}

	@Test
	void testConfigureAuthSkipsNullRepo() {
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		RepoHttpClientFactory factory = new RepoHttpClientFactory(mockClient, false);
		HttpGet request = new HttpGet("https://example.com/index.yaml");
		factory.configureAuth(request, null);
		assertNull(request.getFirstHeader("Authorization"));
	}

	@Test
	void testConfigureAuthHandlesNullPassword() {
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		RepoHttpClientFactory factory = new RepoHttpClientFactory(mockClient, false);
		RepositoryConfig.Repository repo = RepositoryConfig.Repository.builder()
			.name("test")
			.url("https://example.com")
			.username("tokenuser")
			.build();
		HttpGet request = new HttpGet("https://example.com/index.yaml");
		factory.configureAuth(request, repo);
		String expected = "Basic " + Base64.getEncoder().encodeToString("tokenuser:".getBytes(StandardCharsets.UTF_8));
		assertNotNull(request.getFirstHeader("Authorization"));
		assertEquals(expected, request.getFirstHeader("Authorization").getValue());
	}

}
