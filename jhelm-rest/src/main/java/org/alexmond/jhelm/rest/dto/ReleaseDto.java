package org.alexmond.jhelm.rest.dto;

import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.model.Release;

@Data
@Builder
public class ReleaseDto {

	private String name;

	private String namespace;

	private int version;

	private String status;

	private String chartName;

	private String chartVersion;

	private String appVersion;

	private String description;

	private OffsetDateTime lastDeployed;

	public static ReleaseDto from(Release release) {
		ReleaseDto.ReleaseDtoBuilder builder = ReleaseDto.builder()
			.name(release.getName())
			.namespace(release.getNamespace())
			.version(release.getVersion());
		if (release.getChart() != null && release.getChart().getMetadata() != null) {
			builder.chartName(release.getChart().getMetadata().getName())
				.chartVersion(release.getChart().getMetadata().getVersion())
				.appVersion(release.getChart().getMetadata().getAppVersion());
		}
		if (release.getInfo() != null) {
			builder.status(release.getInfo().getStatus())
				.description(release.getInfo().getDescription())
				.lastDeployed(release.getInfo().getLastDeployed());
		}
		return builder.build();
	}

}
