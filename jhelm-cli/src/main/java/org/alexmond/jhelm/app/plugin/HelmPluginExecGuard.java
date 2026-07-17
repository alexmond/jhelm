package org.alexmond.jhelm.app.plugin;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;

/**
 * Deny-by-default gate for running native Helm plugins. A Helm plugin is an arbitrary
 * executable, so jhelm runs one only when the unified {@link JhelmSecurityPolicy} is in
 * {@link JhelmAccessMode#FULL FULL} mode — the same posture the cluster-mutating commands
 * require. In the {@link JhelmAccessMode#READ_ONLY READ_ONLY} posture the invocation is
 * refused with a message pointing at the mode setting.
 *
 * <p>
 * This mirrors the {@code MutatingGuard} used by the mutating commands: {@code FULL}
 * alone unlocks the CLI (the local kubeconfig is already the trust boundary, as with
 * {@code helm}); no API key is required for a local CLI.
 */
public final class HelmPluginExecGuard {

	static final String DISABLED_MESSAGE = "running native Helm plugins is disabled in READ_ONLY mode — set "
			+ "jhelm.security.mode=FULL to enable (e.g. -Djhelm.security.mode=FULL or JHELM_SECURITY_MODE=FULL)";

	private HelmPluginExecGuard() {
	}

	/**
	 * Reports whether running a native Helm plugin must be refused under the current
	 * policy, printing the deny message to stderr when it must.
	 * @param policy the unified security policy
	 * @return {@code true} if plugin execution is blocked, {@code false} if it may
	 * proceed
	 */
	public static boolean blocked(JhelmSecurityPolicy policy) {
		if (policy.mode() == JhelmAccessMode.FULL) {
			return false;
		}
		CliOutput.errPrintln(CliOutput.error(DISABLED_MESSAGE));
		return true;
	}

}
