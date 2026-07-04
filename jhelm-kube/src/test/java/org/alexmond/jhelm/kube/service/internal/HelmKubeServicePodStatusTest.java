package org.alexmond.jhelm.kube.service.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the pod-phase readiness decision behind {@code waitForReady}. A
 * completion-style pod (e.g. a {@code helm test} hook, {@code restartPolicy: Never})
 * terminates in {@code Succeeded} and never reaches {@code Running}, so
 * {@code jhelm test} used to poll it until the wait timed out. {@code Succeeded} must
 * count as done; {@code Failed} is handled separately as a terminal failure the waiter
 * fails fast on.
 */
class HelmKubeServicePodStatusTest {

	@ParameterizedTest
	@CsvSource({
			// phase, readyCondition, expectedReady
			"Succeeded, false, true", // completion pod exited 0 — done even without a
										// Ready condition
			"Succeeded, true, true", "Running, true, true", // long-lived pod, Running +
															// Ready
			"Running, false, false", // Running but not yet Ready
			"Pending, false, false", "Failed, false, false", // terminal failure is not
																// "ready"
			"Unknown, false, false" })
	void testPodReadiness(String phase, boolean readyCondition, boolean expectedReady) {
		assertTrue(HelmKubeService.isPodReady(phase, readyCondition) == expectedReady,
				phase + "/readyCondition=" + readyCondition + " should be ready=" + expectedReady);
	}

	@Test
	void testSucceededIsReadyRegardlessOfReadyCondition() {
		// The key regression: a test-hook pod reaches Succeeded with no Ready condition,
		// and
		// must still be treated as done rather than polled to the timeout.
		assertTrue(HelmKubeService.isPodReady("Succeeded", false));
	}

	@Test
	void testFailedIsNotReady() {
		// Failed is never ready; the waiter treats it as a terminal failure (fail fast).
		assertFalse(HelmKubeService.isPodReady("Failed", false));
	}

}
