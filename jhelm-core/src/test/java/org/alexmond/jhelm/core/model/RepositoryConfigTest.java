package org.alexmond.jhelm.core.model;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryConfigTest {

	private final YAMLMapper mapper = YAMLMapper.builder().build();

	@Test
	void testSnakeCaseWireKeysParseIntoCamelCaseFields() {
		String yaml = """
				name: myrepo
				url: https://example.com
				insecure_skip_tls_verify: true
				pass_credentials_all: true
				""";
		RepositoryConfig.Repository repo = mapper.readValue(yaml, RepositoryConfig.Repository.class);
		assertTrue(repo.isInsecureSkipTlsVerify());
		assertTrue(repo.isPassCredentialsAll());
	}

	@Test
	void testSerializesBackToHelmSnakeCaseKeys() {
		RepositoryConfig.Repository repo = RepositoryConfig.Repository.builder()
			.name("myrepo")
			.url("https://example.com")
			.insecureSkipTlsVerify(true)
			.passCredentialsAll(false)
			.build();
		String yaml = mapper.writeValueAsString(repo);
		assertTrue(yaml.contains("insecure_skip_tls_verify:"), yaml);
		assertTrue(yaml.contains("pass_credentials_all:"), yaml);
		assertFalse(yaml.contains("insecureSkipTlsVerify"), yaml);
	}

}
