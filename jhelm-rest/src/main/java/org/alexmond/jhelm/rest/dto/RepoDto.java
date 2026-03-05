package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.model.RepositoryConfig;

@Data
@Builder
@Schema(description = "Chart repository")
public class RepoDto {

	@Schema(description = "Repository name", example = "bitnami")
	private String name;

	@Schema(description = "Repository URL", example = "https://charts.bitnami.com/bitnami")
	private String url;

	public static RepoDto from(RepositoryConfig.Repository repo) {
		return RepoDto.builder().name(repo.getName()).url(repo.getUrl()).build();
	}

}
