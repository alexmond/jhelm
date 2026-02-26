package org.alexmond.jhelm.plugin.sandbox;

import org.alexmond.jhelm.plugin.exception.PluginExecutionException;
import org.alexmond.jhelm.plugin.exception.PluginTimeoutException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxedExecutorTest {

	private final SandboxedExecutor executor = new SandboxedExecutor();

	@Test
	void executeReturnsResultOnSuccess() throws Exception {
		SandboxConfig config = SandboxConfig.builder().timeoutSeconds(5).build();
		String result = executor.execute("test-plugin", config, () -> "hello");
		assertEquals("hello", result);
	}

	@Test
	void executeThrowsTimeoutOnSlowTask() {
		SandboxConfig config = SandboxConfig.builder().timeoutSeconds(1).build();
		assertThrows(PluginTimeoutException.class, () -> executor.execute("slow-plugin", config, () -> {
			Thread.sleep(5000);
			return "late";
		}));
	}

	@Test
	void executeWrapsExceptionInPluginExecutionException() {
		SandboxConfig config = SandboxConfig.builder().timeoutSeconds(5).build();
		PluginExecutionException ex = assertThrows(PluginExecutionException.class,
				() -> executor.execute("fail-plugin", config, () -> {
					throw new RuntimeException("boom");
				}));
		assertTrue(ex.getMessage().contains("fail-plugin"));
	}

}
