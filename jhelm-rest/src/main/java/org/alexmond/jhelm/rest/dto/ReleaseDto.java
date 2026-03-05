package org.alexmond.jhelm.rest.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.model.Release;

@Data
@Builder
@Schema(description = "Summary of a Helm release")
public class ReleaseDto {

	@Schema(description = "Release name", example = "my-release")
	private String name;

	@Schema(description = "Kubernetes namespace", example = "default")
	private String namespace;

	@Schema(description = "Release revision number", example = "1")
	private int version;

	@Schema(description = "Release status", example = "deployed")
	private String status;

	@Schema(description = "Chart name", example = "nginx")
	private String chartName;

	@Schema(description = "Chart version", example = "1.0.0")
	private String chartVersion;

	@Schema(description = "Application version", example = "1.25")
	private String appVersion;

	@Schema(description = "Status description", example = "Install complete")
	private String description;

	@Schema(description = "Timestamp of last deployment")
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
