package org.alexmond.jhelm.rest.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.model.HelmHook;

@Data
@Builder
@Schema(description = "Helm hook defined in a release")
public class HelmHookDto {

	@Schema(description = "Kubernetes resource kind", example = "Job")
	private String kind;

	@Schema(description = "Hook name", example = "my-release-test")
	private String name;

	@Schema(description = "Hook execution phases", example = "[\"pre-install\", \"pre-upgrade\"]")
	private List<String> phases;

	@Schema(description = "Hook execution weight (lower runs first)", example = "0")
	private int weight;

	@Schema(description = "Hook delete policies", example = "[\"before-hook-creation\"]")
	private List<String> deletePolicy;

	public static HelmHookDto from(HelmHook hook) {
		return HelmHookDto.builder()
			.kind(hook.getKind())
			.name(hook.getName())
			.phases(hook.getPhases())
			.weight(hook.getWeight())
			.deletePolicy(hook.getDeletePolicy())
			.build();
	}

}
