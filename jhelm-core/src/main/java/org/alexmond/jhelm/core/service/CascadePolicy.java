package org.alexmond.jhelm.core.service;

import java.util.Locale;

/**
 * Deletion propagation policy for removing a release's resources, mirroring Helm's
 * {@code --cascade} option and Kubernetes' {@code DeleteOptions.propagationPolicy}.
 */
public enum CascadePolicy {

	/**
	 * Delete the owner immediately and garbage-collect its dependents in the background
	 * (Kubernetes {@code Background}). This is Helm's and Kubernetes' default.
	 */
	BACKGROUND("Background"),

	/**
	 * Block until the owner and all its dependents are deleted (Kubernetes
	 * {@code Foreground}).
	 */
	FOREGROUND("Foreground"),

	/**
	 * Delete only the owner and leave its dependents orphaned (Kubernetes
	 * {@code Orphan}).
	 */
	ORPHAN("Orphan");

	private final String propagationPolicy;

	CascadePolicy(String propagationPolicy) {
		this.propagationPolicy = propagationPolicy;
	}

	/**
	 * @return the Kubernetes {@code DeleteOptions.propagationPolicy} value for this
	 * policy
	 */
	public String propagationPolicy() {
		return this.propagationPolicy;
	}

	/**
	 * Parses Helm's {@code --cascade} argument ({@code background}, {@code foreground},
	 * or {@code orphan}, case-insensitive) into a policy.
	 * @param value the argument value; {@code null} or blank yields {@link #BACKGROUND}
	 * @return the matching policy
	 * @throws IllegalArgumentException if the value is not a recognised cascade mode
	 */
	public static CascadePolicy fromString(String value) {
		if (value == null || value.isBlank()) {
			return BACKGROUND;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "background" -> BACKGROUND;
			case "foreground" -> FOREGROUND;
			case "orphan" -> ORPHAN;
			default -> throw new IllegalArgumentException(
					"invalid cascade '" + value + "' (expected background, foreground, or orphan)");
		};
	}

}
