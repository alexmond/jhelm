package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.model.ResourceStatus;

@Data
@Builder
@Schema(description = "Status of a Kubernetes resource managed by a release")
public class ResourceStatusDto {

	@Schema(description = "Resource kind", example = "Deployment")
	private String kind;

	@Schema(description = "Resource name", example = "my-release-nginx")
	private String name;

	@Schema(description = "Kubernetes namespace", example = "default")
	private String namespace;

	@Schema(description = "Whether the resource is ready")
	private boolean ready;

	@Schema(description = "Status message")
	private String message;

	public static ResourceStatusDto from(ResourceStatus rs) {
		return ResourceStatusDto.builder()
			.kind(rs.getKind())
			.name(rs.getName())
			.namespace(rs.getNamespace())
			.ready(rs.isReady())
			.message(rs.getMessage())
			.build();
	}

}
