package org.alexmond.jhelm.core.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Helm hook resource parsed from a chart manifest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelmHook {

	/** Kubernetes resource kind (e.g. {@code Job}). */
	private String kind;

	/** Resource name within its namespace. */
	private String name;

	/** Namespace the resource lives in. */
	private String namespace;

	/** Hook phases this resource participates in (e.g. {@code pre-install}). */
	private List<String> phases;

	/** Execution order — lower values run first. Default is {@code 0}. */
	private int weight;

	/**
	 * Delete policies from {@code helm.sh/hook-delete-policy} (e.g.
	 * {@code before-hook-creation}, {@code hook-succeeded}).
	 */
	private List<String> deletePolicy;

	/** Raw YAML document for this hook resource. */
	private String yaml;

}
