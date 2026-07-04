package org.alexmond.jhelm.core.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.config.ConfigServerProperties;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.model.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Request resolution (properties merged with per-command overrides), fail-fast behavior,
 * and precedence-flag surfacing for {@link ConfigServerValuesLoader}. The HTTP boundary
 * ({@link ConfigServerClient}) is mocked.
 */
class ConfigServerValuesLoaderTest {

	private final ConfigServerClient client = mock(ConfigServerClient.class);

	private final ConfigServerProperties properties = new ConfigServerProperties();

	private final ConfigServerValuesLoader loader = new ConfigServerValuesLoader(properties, client);

	private static Environment environmentWith(Map<String, Object> flatSource) {
		Environment env = new Environment();
		Environment.PropertySource ps = new Environment.PropertySource();
		ps.setSource(flatSource);
		env.setPropertySources(List.of(ps));
		return env;
	}

	@Test
	void testDisabledByDefaultMakesNoCall() throws IOException {
		ConfigServerValuesLoader.Result result = loader.load("rel", List.of("prod"),
				ConfigServerValuesLoader.Options.none());
		assertTrue(result.values().isEmpty(), "disabled -> empty values");
		verify(client, never()).fetch(any());
	}

	@Test
	void testEnabledViaPropertyFetchesAndMapsWithDefaultName() throws IOException {
		properties.setEnabled(true);
		properties.setUri("http://cfg");
		ArgumentCaptor<ConfigServerRequest> captor = ArgumentCaptor.forClass(ConfigServerRequest.class);
		when(client.fetch(captor.capture())).thenReturn(environmentWith(Map.of("image.tag", "prod")));

		ConfigServerValuesLoader.Result result = loader.load("my-release", List.of("prod"),
				ConfigServerValuesLoader.Options.none());

		ConfigServerRequest req = captor.getValue();
		assertEquals("my-release", req.application(), "application defaults to the release name");
		assertEquals(List.of("prod"), req.profiles(), "active profiles flow into the request");
		assertEquals("prod", ((Map<?, ?>) result.values().get("image")).get("tag"), "fetched values are un-flattened");
	}

	@Test
	void testCliUriEnablesEvenWhenPropertyDisabled() throws IOException {
		when(client.fetch(any())).thenReturn(new Environment());
		ArgumentCaptor<ConfigServerRequest> captor = ArgumentCaptor.forClass(ConfigServerRequest.class);
		when(client.fetch(captor.capture())).thenReturn(new Environment());

		loader.load("rel", List.of(),
				new ConfigServerValuesLoader.Options("http://cli", null, null, null, null, null, null));

		assertEquals("http://cli", captor.getValue().uri());
	}

	@Test
	void testCliOverridesWinOverProperties() throws IOException {
		properties.setEnabled(true);
		properties.setUri("http://prop");
		properties.setName("prop-name");
		properties.setToken("prop-token");
		ArgumentCaptor<ConfigServerRequest> captor = ArgumentCaptor.forClass(ConfigServerRequest.class);
		when(client.fetch(captor.capture())).thenReturn(new Environment());

		loader.load("rel", List.of(), new ConfigServerValuesLoader.Options("http://cli", "cli-name", "cli-label", null,
				null, "cli-token", null));

		ConfigServerRequest req = captor.getValue();
		assertEquals("http://cli", req.uri());
		assertEquals("cli-name", req.application());
		assertEquals("cli-label", req.label());
		assertEquals("cli-token", req.token());
	}

	@Test
	void testFailFastAbortsOnFetchError() throws IOException {
		properties.setEnabled(true);
		properties.setUri("http://cfg");
		properties.setFailFast(true);
		when(client.fetch(any())).thenThrow(new IOException("connection refused"));

		assertThrows(JhelmException.class,
				() -> loader.load("rel", List.of(), ConfigServerValuesLoader.Options.none()));
	}

	@Test
	void testNonFailFastWarnsAndContinues() throws IOException {
		properties.setEnabled(true);
		properties.setUri("http://cfg");
		properties.setFailFast(false);
		when(client.fetch(any())).thenThrow(new IOException("connection refused"));

		ConfigServerValuesLoader.Result result = loader.load("rel", List.of(), ConfigServerValuesLoader.Options.none());
		assertTrue(result.values().isEmpty(), "fetch failure with fail-fast=false yields empty values, no throw");
	}

	@Test
	void testPrecedenceFlagsSurfaced() throws IOException {
		properties.setEnabled(true);
		properties.setUri("http://cfg");
		properties.setOverrideNone(true);
		properties.setOverrideSystemProperties(true);
		when(client.fetch(any())).thenReturn(new Environment());

		ConfigServerValuesLoader.Result result = loader.load("rel", List.of(), ConfigServerValuesLoader.Options.none());
		assertTrue(result.overrideNone());
		assertTrue(result.overrideSystemProperties());
	}

}
