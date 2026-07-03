package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;

/**
 * Shared deny-by-default gate for cluster-mutating CLI commands.
 * <p>
 * The cluster-mutating operations (install, upgrade, uninstall, rollback, test) run only
 * when the unified {@link JhelmSecurityPolicy} is in {@link JhelmAccessMode#FULL FULL}
 * mode; in the default {@link JhelmAccessMode#READ_ONLY READ_ONLY} posture the command
 * refuses, so the CLI's behavior matches the access-mode banner it logs at startup rather
 * than silently mutating the cluster.
 * <p>
 * Unlike the network-exposed {@code jhelm-rest} / {@code jhelm-mcp} adapters — which
 * additionally require a valid API key on each request — the standalone CLI unlocks on
 * {@code mode=FULL} <em>alone</em>. The API key is a transport credential validated
 * against an HTTP header; a local CLI presents no such header, and its kubeconfig is
 * already the trust boundary (the same model as {@code helm}), so demanding an
 * otherwise-unused key would add friction with no security value.
 */
final class MutatingGuard {

	static final String DISABLED_MESSAGE = "mutating operations are disabled in READ_ONLY mode — set "
			+ "jhelm.security.mode=FULL to enable (e.g. -Djhelm.security.mode=FULL or JHELM_SECURITY_MODE=FULL)";

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
		if (policy.mode() == JhelmAccessMode.FULL) {
			return false;
		}
		CliOutput.errPrintln(CliOutput.error(DISABLED_MESSAGE));
		return true;
	}

}
