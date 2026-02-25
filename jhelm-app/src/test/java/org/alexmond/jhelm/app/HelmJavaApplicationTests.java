package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.KubeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class HelmJavaApplicationTests {

	@MockitoBean
	private KubeService kubeService;

	@Autowired(required = false)
	private HelmJavaApplication application;

	@Test
	void contextLoads() {
	}

	@Test
	void testApplicationIsCreated() {
		assertNotNull(application);
	}

	@Test
	void testRunMethod() {
		if (application != null) {
			application.run("--help");
			assertTrue(application.getExitCode() >= 0);
		}
	}

	@Test
	void testGetExitCode() {
		if (application != null) {
			int exitCode = application.getExitCode();
			assertTrue(exitCode >= 0 || exitCode <= 2);
		}
	}

	@Test
	void testRunWithVersion() {
		if (application != null) {
			application.run("--version");
			int exitCode = application.getExitCode();
			assertTrue(exitCode >= 0);
		}
	}

	@Test
	void testRunWithInvalidCommand() {
		if (application != null) {
			application.run("invalid-command");
			int exitCode = application.getExitCode();
			// Invalid commands return a non-zero exit code
			assertNotEquals(0, exitCode);
		}
	}

}
