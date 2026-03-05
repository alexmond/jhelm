package org.alexmond.jhelm.rest.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.model.HelmHook;

@Data
@Builder
public class HelmHookDto {

	private String kind;

	private String name;

	private List<String> phases;

	private int weight;

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
