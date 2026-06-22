package org.alexmond.gotmpl4j.spring;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GoTemplateAvailabilityProvider}, ported from Spring Boot's
 * {@code MustacheTemplateAvailabilityProviderTests}: an existing template is reported
 * available, a missing one is not, and custom prefix/suffix (incl. the deprecated
 * {@code template-location}) are honoured.
 */
class GoTemplateAvailabilityProviderTest {

	private final GoTemplateAvailabilityProvider provider = new GoTemplateAvailabilityProvider();

	private final ResourceLoader resourceLoader = new DefaultResourceLoader();

	private final MockEnvironment environment = new MockEnvironment();

	@Test
	void availableTemplate() {
		assertTrue(isAvailable("hello"));
	}

	@Test
	void unavailableTemplate() {
		assertFalse(isAvailable("does-not-exist"));
	}

	@Test
	void nestedTemplateIsAvailable() {
		assertTrue(isAvailable("layouts/footer"));
	}

	@Test
	void honoursDeprecatedTemplateLocationAlias() {
		this.environment.setProperty("gotmpl4j.template-location", "classpath:/templates/");
		assertTrue(isAvailable("hello"));
	}

	@Test
	void customSuffixThatDoesNotMatchIsUnavailable() {
		this.environment.setProperty("gotmpl4j.suffix", ".nope");
		assertFalse(isAvailable("hello"));
	}

	private boolean isAvailable(String view) {
		return this.provider.isTemplateAvailable(view, this.environment, getClass().getClassLoader(),
				this.resourceLoader);
	}

}
