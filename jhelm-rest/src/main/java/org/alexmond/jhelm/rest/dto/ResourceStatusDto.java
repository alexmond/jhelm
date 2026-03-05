package org.alexmond.jhelm.rest.dto;

import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.model.ResourceStatus;

@Data
@Builder
public class ResourceStatusDto {

	private String kind;

	private String name;

	private String namespace;

	private boolean ready;

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
