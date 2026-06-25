package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.service.RepoManager;

/**
 * Describes a single chart version advertised by a repository index.
 */
@Data
@Builder
@Schema(description = "Available chart version in a repository")
public class ChartVersionDto {

	@Schema(description = "Chart name", example = "nginx")
	private String name;

	@Schema(description = "Chart version", example = "1.0.0")
	private String chartVersion;

	@Schema(description = "Application version", example = "1.25")
	private String appVersion;

	@Schema(description = "Chart description")
	private String description;

	/**
	 * Maps a core chart-version model to its REST representation.
	 * @param cv the source chart version from the repository index
	 * @return the populated DTO
	 */
	public static ChartVersionDto from(RepoManager.ChartVersion cv) {
		return ChartVersionDto.builder()
			.name(cv.getName())
			.chartVersion(cv.getChartVersion())
			.appVersion(cv.getAppVersion())
			.description(cv.getDescription())
			.build();
	}

}
