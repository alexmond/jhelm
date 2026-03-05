package org.alexmond.jhelm.rest.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.rest.dto.HubSearchResultDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${jhelm.rest.base-path:/api/v1}/hub")
@Tag(name = "Hub", description = "Search ArtifactHub for charts")
public class HubController {

	private final SearchHubAction searchHubAction;

	public HubController(SearchHubAction searchHubAction) {
		this.searchHubAction = searchHubAction;
	}

	@GetMapping("/search")
	@Operation(summary = "Search ArtifactHub", description = "Search for Helm charts on ArtifactHub")
	public List<HubSearchResultDto> search(@Parameter(description = "Search keyword") @RequestParam String keyword,
			@Parameter(description = "Maximum results (max 60)") @RequestParam(defaultValue = "20") int maxResults)
			throws Exception {
		return this.searchHubAction.search(keyword, maxResults).stream().map(HubSearchResultDto::from).toList();
	}

}
