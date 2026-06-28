package org.alexmond.jhelm.core.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The effective, resolved security posture for jhelm adapters, derived from
 * {@link JhelmSecurityProperties}.
 *
 * <p>
 * <strong>Deny-by-default:</strong> mutating (cluster-changing) operations are enabled
 * <em>only</em> when the operator both selects {@link JhelmAccessMode#FULL FULL}
 * <em>and</em> configures an API key. Requesting {@code FULL} without a key does
 * <em>not</em> enable mutating operations — the safe default wins so an adapter cannot be
 * accidentally started as an open, unauthenticated cluster-mutation API.
 */
public final class JhelmSecurityPolicy {

	private final JhelmAccessMode mode;

	private final String apiKey;

	private final String apiKeyHeader;

	/**
	 * Creates a policy snapshot from the supplied properties.
	 * @param properties the unified security properties
	 */
	public JhelmSecurityPolicy(JhelmSecurityProperties properties) {
		this.mode = properties.getMode();
		this.apiKey = properties.getApiKey();
		this.apiKeyHeader = properties.getApiKeyHeader();
	}

	/**
	 * Returns the configured access mode.
	 * @return the access mode
	 */
	public JhelmAccessMode mode() {
		return this.mode;
	}

	/**
	 * Indicates whether a non-blank API key is configured.
	 * @return {@code true} if an API key is set and not blank
	 */
	public boolean apiKeyConfigured() {
		return this.apiKey != null && !this.apiKey.isBlank();
	}

	/**
	 * Indicates whether cluster-mutating operations are enabled.
	 *
	 * <p>
	 * <strong>Deny-by-default:</strong> this returns {@code true} only when the mode is
	 * {@link JhelmAccessMode#FULL FULL} <em>and</em> an API key is configured. Selecting
	 * {@code FULL} without a key does <em>not</em> enable mutating operations.
	 * @return {@code true} if mutating operations are enabled
	 */
	public boolean mutatingEnabled() {
		return this.mode == JhelmAccessMode.FULL && apiKeyConfigured();
	}

	/**
	 * Validates a presented API key against the configured key using a constant-time
	 * comparison (resistant to timing attacks). Returns {@code false} when no key is
	 * configured or the presented value is {@code null}.
	 * @param presented the API key presented by the caller
	 * @return {@code true} if the presented key matches the configured key
	 */
	public boolean validApiKey(String presented) {
		return apiKeyConfigured() && presented != null && MessageDigest
			.isEqual(this.apiKey.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Returns the request header name that carries the API key.
	 * @return the API key header name
	 */
	public String apiKeyHeader() {
		return this.apiKeyHeader;
	}

}
