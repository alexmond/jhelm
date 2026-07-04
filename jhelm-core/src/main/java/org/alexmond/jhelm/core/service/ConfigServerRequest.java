package org.alexmond.jhelm.core.service;

import java.util.List;

/**
 * A resolved config-server fetch request: the effective values after merging
 * {@code jhelm.config-server.*} properties with per-command {@code --config-*} overrides.
 *
 * @param uri config-server base URI (no trailing path)
 * @param application the {@code {application}} path segment (release name by default)
 * @param profiles active profiles for the {@code {profiles}} segment (comma-joined; empty
 * falls back to {@code default})
 * @param label optional {@code {label}} segment (git branch); {@code null}/blank omits it
 * @param username basic-auth username, or {@code null}
 * @param password basic-auth password, or {@code null}
 * @param token bearer token (takes precedence over basic auth), or {@code null}
 */
public record ConfigServerRequest(String uri, String application, List<String> profiles, String label, String username,
		String password, String token) {
}
