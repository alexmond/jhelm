package org.alexmond.jhelm.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RegistryManagerTest {

	@TempDir
	Path tempDir;

	private RegistryManager registryManager;

	@BeforeEach
	void setUp() {
		// Back the manager with a temp config file so the login/logout tests below never
		// touch the developer's real ~/.config/helm/registry/config.json.
		registryManager = new RegistryManager(tempDir.resolve("registry-config.json").toString());
	}

	@Test
	void testDefaultConstructorCreatesConfigPath() {
		// exercises the OS-specific default path selection without writing anything
		assertNotNull(new RegistryManager());
	}

	@Test
	void testLoadConfigReturnsEmptyConfigWhenFileMissing() throws IOException {
		RegistryManager.Config config = registryManager.loadConfig();
		assertNotNull(config);
		assertNotNull(config.getAuths());
	}

	@Test
	void testLoginWritesOwnerOnlyCredentialsFile() throws IOException {
		Path configFile = tempDir.resolve("registry-config.json");
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
				"POSIX permissions not supported on this filesystem");

		registryManager.login("ghcr.io", "user", "pass");

		Set<PosixFilePermission> perms = Files.getPosixFilePermissions(configFile);
		// credentials file must be owner-only (0600) — no group/other access
		assertEquals(PosixFilePermissions.fromString("rw-------"), perms);
	}

	@Test
	void testLoadConfigReadsPersistedFile() throws IOException {
		// login writes the file; a fresh manager on the same path must read it back
		// (covers the loadConfig file-exists branch)
		registryManager.login("ghcr.io", "user", "pass");
		RegistryManager reopened = new RegistryManager(tempDir.resolve("registry-config.json").toString());
		assertNotNull(reopened.getAuth("ghcr.io"));
	}

	@Test
	void testLoginStoresCredentials() throws IOException {
		String registry = "ghcr.io";
		String username = "testuser";
		String password = "testpass";

		registryManager.login(registry, username, password);

		String auth = registryManager.getAuth(registry);
		assertNotNull(auth);

		String expectedAuth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
		assertEquals(expectedAuth, auth);
	}

	@Test
	void testLogoutRemovesCredentials() throws IOException {
		String registry = "docker.io";
		String username = "user";
		String password = "pass";

		registryManager.login(registry, username, password);
		assertNotNull(registryManager.getAuth(registry));

		registryManager.logout(registry);
		assertNull(registryManager.getAuth(registry));
	}

	@Test
	void testGetAuthReturnsNullForNonExistentRegistry() throws IOException {
		String auth = registryManager.getAuth("nonexistent.registry.io");
		assertNull(auth);
	}

	@Test
	void testMultipleRegistries() throws IOException {
		registryManager.login("registry1.io", "user1", "pass1");
		registryManager.login("registry2.io", "user2", "pass2");

		assertNotNull(registryManager.getAuth("registry1.io"));
		assertNotNull(registryManager.getAuth("registry2.io"));

		registryManager.logout("registry1.io");
		assertNull(registryManager.getAuth("registry1.io"));
		assertNotNull(registryManager.getAuth("registry2.io"));
	}

	@Test
	void testLoginOverwritesExistingCredentials() throws IOException {
		String registry = "example.io";

		registryManager.login(registry, "olduser", "oldpass");
		String oldAuth = registryManager.getAuth(registry);

		registryManager.login(registry, "newuser", "newpass");
		String newAuth = registryManager.getAuth(registry);

		assertNotNull(oldAuth);
		assertNotNull(newAuth);
		assertNotEquals(oldAuth, newAuth);

		String expectedNewAuth = Base64.getEncoder().encodeToString("newuser:newpass".getBytes());
		assertEquals(expectedNewAuth, newAuth);
	}

	@Test
	void testConfigBuilder() {
		RegistryManager.Config config = RegistryManager.Config.builder().build();
		assertNotNull(config);
		assertNotNull(config.getAuths());
	}

	@Test
	void testAuthBuilder() {
		String authString = "dGVzdDp0ZXN0";
		RegistryManager.Config.Auth auth = RegistryManager.Config.Auth.builder().auth(authString).build();
		assertNotNull(auth);
		assertEquals(authString, auth.getAuth());
	}

}
