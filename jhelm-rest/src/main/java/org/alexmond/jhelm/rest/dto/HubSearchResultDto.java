package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.action.SearchHubAction;

@Data
@Builder
@Schema(description = "ArtifactHub search result")
public class HubSearchResultDto {

	@Schema(description = "Chart name", example = "nginx")
	private String name;

	@Schema(description = "Chart description")
	private String description;

	@Schema(description = "Chart version", example = "1.0.0")
	private String version;

	@Schema(description = "Application version", example = "1.25")
	private String appVersion;

	@Schema(description = "Repository URL", example = "https://charts.bitnami.com/bitnami")
	private String repoUrl;

	@Schema(description = "Repository name", example = "bitnami")
	private String repoName;

	@Schema(description = "ArtifactHub URL")
	private String url;

	public static HubSearchResultDto from(SearchHubAction.HubResult result) {
		return HubSearchResultDto.builder()
			.name(result.getName())
			.description(result.getDescription())
			.version(result.getVersion())
			.appVersion(result.getAppVersion())
			.repoUrl(result.getRepoUrl())
			.repoName(result.getRepoName())
			.url(result.getUrl())
			.build();
	}

}
