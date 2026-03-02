package org.alexmond.jhelm.core.action;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Searches Artifact Hub for Helm charts matching a keyword.
 */
@Slf4j
public class SearchHubAction {

	private static final String ARTIFACT_HUB_API = "https://artifacthub.io/api/v1/packages/search";

	private final CloseableHttpClient httpClient;

	private final JsonMapper jsonMapper;

	public SearchHubAction(CloseableHttpClient httpClient) {
		this.httpClient = httpClient;
		this.jsonMapper = JsonMapper.builder().build();
	}

	public List<HubResult> search(String keyword, int maxResults) throws IOException {
		String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
		String url = ARTIFACT_HUB_API + "?ts_query_web=" + encoded + "&kind=0&limit=" + maxResults;

		HttpGet request = new HttpGet(url);
		request.setHeader("Accept", "application/json");

		return httpClient.execute(request, (response) -> {
			int status = response.getCode();
			String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			if (status != 200) {
				throw new IOException("Artifact Hub returned HTTP " + status);
			}
			return parseResponse(body);
		});
	}

	private List<HubResult> parseResponse(String json) throws IOException {
		List<HubResult> results = new ArrayList<>();
		JsonNode root = jsonMapper.readTree(json);
		JsonNode packages = root.get("packages");
		if (packages == null || !packages.isArray()) {
			return results;
		}
		for (JsonNode pkg : packages) {
			HubResult result = new HubResult();
			result.setName(textOrEmpty(pkg, "name"));
			result.setDescription(textOrEmpty(pkg, "description"));
			result.setVersion(textOrEmpty(pkg, "version"));
			result.setAppVersion(textOrEmpty(pkg, "app_version"));
			JsonNode repo = pkg.get("repository");
			if (repo != null) {
				result.setRepoUrl(textOrEmpty(repo, "url"));
				result.setRepoName(textOrEmpty(repo, "name"));
			}
			results.add(result);
		}
		return results;
	}

	private String textOrEmpty(JsonNode node, String field) {
		JsonNode child = node.get(field);
		return (child != null && !child.isNull()) ? child.asText() : "";
	}

	@Data
	public static class HubResult {

		private String name;

		private String description;

		private String version;

		private String appVersion;

		private String repoUrl;

		private String repoName;

		/**
		 * Returns the full chart URL on Artifact Hub.
		 */
		public String getUrl() {
			if (!repoName.isEmpty() && !name.isEmpty()) {
				return "https://artifacthub.io/packages/helm/" + repoName + "/" + name;
			}
			return "";
		}

	}

}
