package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;

/**
 * Shared deny-by-default gate for cluster-mutating CLI commands.
 * <p>
 * Mirrors the {@code jhelm-rest} / {@code jhelm-mcp} adapters: the cluster-mutating
 * operations (install, upgrade, uninstall, rollback, test) run only when the unified
 * {@link JhelmSecurityPolicy} has mutating operations enabled — that is,
 * {@code jhelm.security.mode=FULL} <em>and</em> an API key are configured. In the default
 * {@code READ_ONLY} posture the command refuses, so the CLI's behavior matches the
 * access-mode banner it logs at startup rather than silently mutating the cluster.
 */
final class MutatingGuard {

	static final String DISABLED_MESSAGE = "mutating operations are disabled — set jhelm.security.mode=FULL "
			+ "and jhelm.security.api-key to enable";

	private MutatingGuard() {
	}

	/**
	 * Reports whether a cluster-mutating command must be refused under the current
	 * policy, printing the deny message to stderr when it must. The caller returns a
	 * non-zero exit code (typically {@code CommandLine.ExitCode.SOFTWARE}) when this
	 * returns {@code true}.
	 * @param policy the unified security policy
	 * @return {@code true} if the operation is blocked (deny-by-default), {@code false}
	 * if it may proceed
	 */
	static boolean blocked(JhelmSecurityPolicy policy) {
		if (policy.mutatingEnabled()) {
			return false;
		}
		CliOutput.errPrintln(CliOutput.error(DISABLED_MESSAGE));
		return true;
	}

}
