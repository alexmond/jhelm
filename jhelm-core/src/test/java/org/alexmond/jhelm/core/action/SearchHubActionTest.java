package org.alexmond.jhelm.core.action;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchHubActionTest {

	@SuppressWarnings("unchecked")
	@Test
	void testSearchReturnsResults() throws Exception {
		String json = """
				{"packages": [
				  {
				    "name": "nginx",
				    "description": "A chart for nginx",
				    "version": "15.0.0",
				    "app_version": "1.25.0",
				    "repository": {"name": "bitnami", "url": "https://charts.bitnami.com/bitnami"}
				  },
				  {
				    "name": "nginx-ingress",
				    "description": "NGINX Ingress Controller",
				    "version": "1.3.0",
				    "app_version": "3.4.0",
				    "repository": {"name": "nginx-stable", "url": "https://helm.nginx.com/stable"}
				  }
				]}""";

		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		when(mockClient.execute(isA(HttpGet.class), any(HttpClientResponseHandler.class))).thenAnswer((inv) -> {
			HttpClientResponseHandler<?> handler = inv.getArgument(1);
			ClassicHttpResponse resp = mock(ClassicHttpResponse.class);
			when(resp.getCode()).thenReturn(200);
			when(resp.getEntity()).thenReturn(new StringEntity(json, StandardCharsets.UTF_8));
			return handler.handleResponse(resp);
		});

		SearchHubAction action = new SearchHubAction(mockClient);
		List<SearchHubAction.HubResult> results = action.search("nginx", 25);

		assertEquals(2, results.size());
		assertEquals("nginx", results.get(0).getName());
		assertEquals("A chart for nginx", results.get(0).getDescription());
		assertEquals("15.0.0", results.get(0).getVersion());
		assertEquals("1.25.0", results.get(0).getAppVersion());
		assertEquals("bitnami", results.get(0).getRepoName());
		assertEquals("https://artifacthub.io/packages/helm/bitnami/nginx", results.get(0).getUrl());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSearchEmptyResult() throws Exception {
		String json = """
				{"packages": []}""";

		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		when(mockClient.execute(isA(HttpGet.class), any(HttpClientResponseHandler.class))).thenAnswer((inv) -> {
			HttpClientResponseHandler<?> handler = inv.getArgument(1);
			ClassicHttpResponse resp = mock(ClassicHttpResponse.class);
			when(resp.getCode()).thenReturn(200);
			when(resp.getEntity()).thenReturn(new StringEntity(json, StandardCharsets.UTF_8));
			return handler.handleResponse(resp);
		});

		SearchHubAction action = new SearchHubAction(mockClient);
		List<SearchHubAction.HubResult> results = action.search("nonexistent", 25);

		assertTrue(results.isEmpty());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSearchHttpError() throws Exception {
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		when(mockClient.execute(isA(HttpGet.class), any(HttpClientResponseHandler.class))).thenAnswer((inv) -> {
			HttpClientResponseHandler<?> handler = inv.getArgument(1);
			ClassicHttpResponse resp = mock(ClassicHttpResponse.class);
			when(resp.getCode()).thenReturn(500);
			when(resp.getEntity()).thenReturn(new StringEntity("error", StandardCharsets.UTF_8));
			return handler.handleResponse(resp);
		});

		SearchHubAction action = new SearchHubAction(mockClient);
		assertThrows(IOException.class, () -> action.search("nginx", 25));
	}

	@Test
	void testHubResultUrl() {
		SearchHubAction.HubResult result = new SearchHubAction.HubResult();
		result.setRepoName("bitnami");
		result.setName("nginx");
		assertEquals("https://artifacthub.io/packages/helm/bitnami/nginx", result.getUrl());
	}

	@Test
	void testHubResultUrlEmpty() {
		SearchHubAction.HubResult result = new SearchHubAction.HubResult();
		result.setRepoName("");
		result.setName("");
		assertEquals("", result.getUrl());
	}

}
