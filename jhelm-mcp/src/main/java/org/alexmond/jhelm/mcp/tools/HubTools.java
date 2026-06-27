package org.alexmond.jhelm.mcp.tools;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * Read-only MCP tool for searching Artifact Hub for Helm charts. The annotated method is
 * discovered by Spring AI's MCP annotation scanner and exposed as an MCP tool.
 */
@RequiredArgsConstructor
public class HubTools {

	private final SearchHubAction searchHubAction;

	/**
	 * Searches Artifact Hub for Helm charts matching a keyword, like
	 * {@code helm search hub}.
	 * @param keyword the search term
	 * @param maxResults the maximum number of results to return
	 * @return a human-readable, newline-separated list of matching charts
	 */
	@McpTool(name = "helm_search_hub",
			description = "Search Artifact Hub for Helm charts matching a keyword (like 'helm search hub'). "
					+ "Read-only.")
	public String searchHub(@McpToolParam(description = "Keyword to search Artifact Hub for") String keyword,
			@McpToolParam(required = false, description = "Maximum number of results to return") int maxResults) {
		int limit = (maxResults > 0) ? maxResults : 25;
		List<SearchHubAction.HubResult> results = this.searchHubAction.search(keyword, limit);
		if (results.isEmpty()) {
			return "No charts found for: " + keyword;
		}
		StringBuilder sb = new StringBuilder();
		for (SearchHubAction.HubResult result : results) {
			sb.append(result.getRepoName())
				.append('/')
				.append(result.getName())
				.append("\tv")
				.append(result.getVersion())
				.append("\tapp ")
				.append(result.getAppVersion())
				.append('\t')
				.append(result.getUrl())
				.append('\n');
		}
		return sb.toString();
	}

}
