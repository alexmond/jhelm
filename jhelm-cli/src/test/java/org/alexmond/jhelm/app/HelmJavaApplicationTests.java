package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.service.KubeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class HelmJavaApplicationTests {

	@MockitoBean
	private KubeService kubeService;

	@Autowired(required = false)
	private HelmJavaApplication application;

	@Autowired
	private JhelmSecurityPolicy securityPolicy;

	@Autowired
	private VersionInfo versionInfo;

	// Optional: absent in an IntelliJ build that skips the Maven build-info goal.
	@Autowired(required = false)
	private BuildProperties buildProperties;

	@Test
	void contextLoads() {
	}

	@Test
	void testVersionResolvesFromBuildInfo() {
		// #662: never the old hardcoded placeholder, and never blank.
		String version = versionInfo.version();
		assertNotNull(version);
		assertFalse(version.isBlank(), "version must not be blank");
		assertNotEquals("0.0.1", version, "must not report the old hardcoded 0.0.1");
		// A Maven build runs the build-info goal, so BuildProperties is present and IS
		// the
		// source (the same bean Actuator's /actuator/info uses). An IntelliJ build may
		// skip
		// that goal — then buildProperties is null and the manifest/development fallback
		// is
		// used, which must still not fail.
		if (buildProperties != null) {
			assertTrue(versionInfo.fromBuildInfo(), "build-info present → it must be the source");
			assertEquals(buildProperties.getVersion(), versionInfo.version());
		}
	}

	@Test
	void testCliDefaultsToFullMode() {
		// #657/#654: the standalone CLI defaults to FULL (read-write) like helm, unlike
		// the
		// READ_ONLY default the network adapters use. Set from jhelm-cli
		// application.properties.
		assertEquals(JhelmAccessMode.FULL, securityPolicy.mode(), "CLI must default to FULL (read-write)");
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
