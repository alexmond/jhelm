package org.alexmond.jhelm.core.util;

import java.util.regex.Pattern;

/**
 * RFC-1123 (Kubernetes DNS) validation for Helm release names and namespaces.
 *
 * <p>
 * A release name is embedded in the release-storage Secret name
 * ({@code sh.helm.release.v1.<name>.v<rev>}) and stamped into resource labels, and a
 * namespace must be a DNS-1123 label, so both are validated at the action boundary before
 * any Secret name or label is built — an invalid name would otherwise produce a malformed
 * Secret name or labels rejected by the Kubernetes API.
 */
public final class ReleaseNames {

	/**
	 * Helm caps release names at 53 characters (leaves room in the 253-char Secret name).
	 */
	private static final int MAX_RELEASE_NAME = 53;

	/** Kubernetes namespaces are DNS-1123 labels, capped at 63 characters. */
	private static final int MAX_NAMESPACE = 63;

	/**
	 * DNS-1123 subdomain: lowercase alphanumeric, {@code -} or {@code .}, start/end
	 * alphanumeric.
	 */
	private static final Pattern RELEASE_NAME = Pattern.compile("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$");

	/** DNS-1123 label: lowercase alphanumeric or {@code -}, start/end alphanumeric. */
	private static final Pattern NAMESPACE = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

	private ReleaseNames() {
	}

	/**
	 * Validates a Helm release name (RFC-1123, &le; 53 chars).
	 * @param name the release name
	 * @throws IllegalArgumentException if the name is empty, too long, or not RFC-1123
	 */
	public static void validateReleaseName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Release name must not be empty");
		}
		if (name.length() > MAX_RELEASE_NAME) {
			throw new IllegalArgumentException(
					"Release name '" + name + "' exceeds the " + MAX_RELEASE_NAME + "-character limit");
		}
		if (!RELEASE_NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("Invalid release name '" + name
					+ "': must be a lowercase RFC-1123 name (alphanumeric, '-' or '.', starting and ending "
					+ "with alphanumeric)");
		}
	}

	/**
	 * Validates a Kubernetes namespace (RFC-1123 label, &le; 63 chars). A {@code null} or
	 * blank namespace is allowed and means "the default namespace", resolved downstream.
	 * @param namespace the namespace, or {@code null}/blank for the default
	 * @throws IllegalArgumentException if a non-blank namespace is too long or not
	 * RFC-1123
	 */
	public static void validateNamespace(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			return;
		}
		if (namespace.length() > MAX_NAMESPACE || !NAMESPACE.matcher(namespace).matches()) {
			throw new IllegalArgumentException("Invalid namespace '" + namespace
					+ "': must be a lowercase RFC-1123 label (alphanumeric or '-', starting and ending with "
					+ "alphanumeric, at most " + MAX_NAMESPACE + " characters)");
		}
	}

}
