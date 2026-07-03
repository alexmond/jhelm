package org.alexmond.jhelm.app;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for #662: the CLI reports its real build version — from Spring Boot
 * build-info ({@link BuildProperties}, the same source Actuator's {@code /actuator/info}
 * uses) — never the removed hardcoded {@code 0.0.1} placeholder, and never failing when
 * build-info is absent (e.g. an IntelliJ build that skips the Maven {@code build-info}
 * goal).
 */
class VersionInfoTest {

	@Test
	void testVersionComesFromBuildInfo() {
		Properties props = new Properties();
		props.setProperty("version", "1.2.3");
		VersionInfo versionInfo = new VersionInfo(new BuildProperties(props));

		assertEquals("1.2.3", versionInfo.version());
		assertTrue(versionInfo.fromBuildInfo(), "should report build-info as the source");
	}

	@Test
	void testDoesNotFailWhenBuildInfoAbsent() {
		// #662 / IntelliJ builds: no build-info bean — must fall back, not throw.
		VersionInfo versionInfo = new VersionInfo((BuildProperties) null);

		String version = versionInfo.version();
		assertFalse(version.isBlank(), "version must not be blank");
		assertNotEquals("0.0.1", version, "must not report the old hardcoded placeholder");
		assertFalse(versionInfo.fromBuildInfo());
	}

}
