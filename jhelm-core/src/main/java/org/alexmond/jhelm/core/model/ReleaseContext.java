package org.alexmond.jhelm.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

/**
 * Helm's {@code .Release} built-in object, exposed to chart templates during rendering.
 *
 * <p>
 * This immutable value type captures the release metadata that templates can reference as
 * {@code .Release.Name}, {@code .Release.Namespace}, {@code .Release.IsInstall}, and so
 * on. {@link #toMap()} produces the wire form consumed by the template engine — a
 * {@link LinkedHashMap} whose keys, value types, and ordering match exactly what the
 * actions historically built by hand, so template resolution stays byte-identical.
 */
@Getter
@Builder
public final class ReleaseContext {

	/** The release name, exposed as {@code .Release.Name}. */
	private final String name;

	/** The target namespace, exposed as {@code .Release.Namespace}. */
	private final String namespace;

	/**
	 * The rendering service, always {@code "Helm"}, exposed as {@code .Release.Service}.
	 */
	@Builder.Default
	private final String service = "Helm";

	/**
	 * Whether this render is part of an install, exposed as {@code .Release.IsInstall}.
	 */
	private final boolean install;

	/**
	 * Whether this render is part of an upgrade, exposed as {@code .Release.IsUpgrade}.
	 */
	private final boolean upgrade;

	/** The release revision number, exposed as {@code .Release.Revision}. */
	private final int revision;

	/**
	 * Builds the {@code .Release} map in the exact form the template engine consumes.
	 *
	 * <p>
	 * The returned {@link LinkedHashMap} preserves the historical key order
	 * ({@code Name}, {@code Namespace}, {@code Service}, {@code IsInstall},
	 * {@code IsUpgrade}, {@code Revision}) and value types ({@code IsInstall}/
	 * {@code IsUpgrade} as booleans, {@code Revision} as an int), guaranteeing
	 * byte-identical template output.
	 * @return the {@code .Release} map exposed to templates
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("Name", name);
		map.put("Namespace", namespace);
		map.put("Service", service);
		map.put("IsInstall", install);
		map.put("IsUpgrade", upgrade);
		map.put("Revision", revision);
		return map;
	}

}
