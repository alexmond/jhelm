package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvCommandTest {

	@Test
	void testEnvPrintsKeyValues() {
		RepoManager repoManager = new RepoManager("/tmp/jhelm-env/repositories.yaml");
		RegistryManager registryManager = new RegistryManager("/tmp/jhelm-env/registry/config.json");
		JhelmKubernetesProperties kubeProperties = new JhelmKubernetesProperties();
		kubeProperties.setKubeconfigPath("/tmp/jhelm-env/kubeconfig");

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintStream original = System.out;
		System.setOut(new PrintStream(bos, true, StandardCharsets.UTF_8));
		try {
			new CommandLine(new EnvCommand(repoManager, registryManager, kubeProperties)).execute();
		}
		finally {
			System.setOut(original);
		}
		String out = bos.toString(StandardCharsets.UTF_8);
		// the base info Helm users expect
		assertTrue(out.contains("HELM_NAMESPACE="), out);
		assertTrue(out.contains("JHELM_VERSION="), out);
		assertTrue(out.contains("JAVA_VERSION="), out);
		// the effective Helm config locations jhelm resolves and honors
		assertTrue(out.contains("HELM_REPOSITORY_CONFIG=\"/tmp/jhelm-env/repositories.yaml\""), out);
		assertTrue(out.contains("HELM_REGISTRY_CONFIG=\"/tmp/jhelm-env/registry/config.json\""), out);
		assertTrue(out.contains("HELM_REPOSITORY_CACHE="), out);
		assertTrue(out.contains("KUBECONFIG=\"/tmp/jhelm-env/kubeconfig\""), out);
	}

	@Test
	void testEnvKubeconfigFallsBackWhenNoOverride() {
		// no jhelm.kubernetes.kubeconfig-path set -> falls back to $KUBECONFIG or
		// ~/.kube/config
		RepoManager repoManager = new RepoManager("/tmp/jhelm-env/repositories.yaml");
		RegistryManager registryManager = new RegistryManager("/tmp/jhelm-env/registry/config.json");
		JhelmKubernetesProperties kubeProperties = new JhelmKubernetesProperties();

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintStream original = System.out;
		System.setOut(new PrintStream(bos, true, StandardCharsets.UTF_8));
		try {
			new CommandLine(new EnvCommand(repoManager, registryManager, kubeProperties)).execute();
		}
		finally {
			System.setOut(original);
		}
		String out = bos.toString(StandardCharsets.UTF_8);
		String expected = (System.getenv("KUBECONFIG") != null) ? System.getenv("KUBECONFIG")
				: System.getProperty("user.home") + "/.kube/config";
		assertTrue(out.contains("KUBECONFIG=\"" + expected + "\""), out);
	}

}
