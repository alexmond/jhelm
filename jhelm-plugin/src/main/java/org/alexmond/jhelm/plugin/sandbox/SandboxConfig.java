package org.alexmond.jhelm.plugin.sandbox;

import lombok.Builder;
import lombok.Data;

/**
 * Per-plugin resource limits for sandboxed execution.
 */
@Data
@Builder
public class SandboxConfig {

	@Builder.Default
	private int timeoutSeconds = 30;

	@Builder.Default
	private int memoryLimitPages = 256;

}
