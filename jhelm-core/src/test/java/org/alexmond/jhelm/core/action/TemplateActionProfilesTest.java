package org.alexmond.jhelm.core.action;

import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.util.ValuesProfiles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end render test (real {@link Engine} + {@link ChartLoader}) for value profiles
 * on the {@code profiles-test} chart: {@code spring.config.activate.on-profile} document
 * gating, {@code values-<profile>.yaml} sidecars, and the guarantee that the activation
 * directive never leaks into {@code .Values}.
 */
class TemplateActionProfilesTest {

	private static final String CHART = "src/test/resources/test-charts/profiles-test";

	private final TemplateAction templateAction = new TemplateAction(new Engine(), new ChartLoader());

	private String render(ValuesProfiles profiles) {
		return templateAction.render(CHART, "r", "default", Map.of(), profiles, null, List.of());
	}

	@Test
	void testNoProfileUsesBaseDocumentOnly() {
		String out = render(ValuesProfiles.none());
		assertTrue(out.contains("replicas: \"1\""), "base replicas when no profile active");
		assertTrue(out.contains("tag: \"base\""), "base image tag when no profile active");
	}

	@Test
	void testProdProfileAppliesGatedDocument() {
		String out = render(ValuesProfiles.of(List.of("prod")));
		assertTrue(out.contains("replicas: \"3\""), "prod document applies replicas");
		assertTrue(out.contains("tag: \"prod\""), "prod document applies image tag");
	}

	@Test
	void testStagingProfileMergesSidecarFile() {
		String out = render(ValuesProfiles.of(List.of("staging")));
		assertTrue(out.contains("replicas: \"2\""), "values-staging.yaml sidecar applies replicas");
		assertTrue(out.contains("tag: \"staging\""), "values-staging.yaml sidecar applies image tag");
	}

	@Test
	void testActivationDirectiveNeverLeaksIntoValues() {
		// The rendered ConfigMap dumps the whole .Values tree via toYaml; the directive
		// key
		// must never appear there, under any profile.
		for (ValuesProfiles profiles : List.of(ValuesProfiles.none(), ValuesProfiles.of(List.of("prod")),
				ValuesProfiles.of(List.of("staging")))) {
			String out = render(profiles);
			assertFalse(out.contains("on-profile"), "activation directive must not leak into .Values");
		}
	}

}
