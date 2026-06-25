package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.model.ResourceStatus;

/**
 * Status of a single Kubernetes resource managed by a release, including readiness.
 */
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

	/**
	 * Maps a core resource-status model to its REST representation.
	 * @param rs the source resource status
	 * @return the populated DTO
	 */
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
