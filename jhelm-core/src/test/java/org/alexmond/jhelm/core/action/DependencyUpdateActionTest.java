package org.alexmond.jhelm.core.action;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.alexmond.jhelm.core.model.ChartLock;
import org.alexmond.jhelm.core.model.ChartLock.LockDependency;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.core.service.RepoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependencyUpdateActionTest {

	@Mock
	private RepoManager repoManager;

	@Mock
	private DependencyResolver resolver;

	@TempDir
	Path tempDir;

	private DependencyUpdateAction action;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.action = new DependencyUpdateAction(this.repoManager, this.resolver);
	}

	private void writeChart(String dependenciesBlock) throws Exception {
		Files.writeString(tempDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: parent
				version: 1.0.0
				""" + dependenciesBlock);
	}

	@Test
	void throwsWhenChartPathIsNotADirectory() {
		File missing = tempDir.resolve("nope").toFile();
		assertThrows(IllegalArgumentException.class, () -> this.action.update(missing, List.of(), false));
	}

	@Test
	void noDependenciesReturnsEmptyLockWithoutWritingChartLock() throws Exception {
		writeChart("");

		ChartLock lock = this.action.update(tempDir.toFile(), List.of(), false);

		assertTrue(lock.getDependencies().isEmpty());
		assertFalse(Files.exists(tempDir.resolve("Chart.lock")), "no Chart.lock is written when there are no deps");
		verify(this.repoManager).updateAll();
	}

	@Test
	void skipRefreshDoesNotUpdateRepositories() throws Exception {
		writeChart("");

		this.action.update(tempDir.toFile(), List.of(), true);

		verify(this.repoManager, never()).updateAll();
	}

	@Test
	void resolvesDownloadsAndWritesLockWhenDependenciesPresent() throws Exception {
		writeChart("""
				dependencies:
				  - name: redis
				    version: "17.0.0"
				    repository: "https://charts.example.com"
				""");
		Files.writeString(tempDir.resolve("values.yaml"), "redis:\n  enabled: true\n");
		ChartLock resolved = ChartLock.builder()
			.dependencies(List.of(LockDependency.builder().name("redis").version("17.0.0").build()))
			.digest("sha256:abc")
			.build();
		when(this.resolver.resolveDependencies(any(), any(), any())).thenReturn(resolved);

		ChartLock lock = this.action.update(tempDir.toFile(), List.of(), false);

		assertEquals(1, lock.getDependencies().size());
		verify(this.resolver).downloadDependencies(eq(tempDir.toFile()), anyList());
		assertTrue(Files.exists(tempDir.resolve("Chart.lock")), "Chart.lock is written when deps resolve");
	}

	@Test
	void passesEnabledTagsThroughAndNullsEmptyTags() throws Exception {
		writeChart("""
				dependencies:
				  - name: redis
				    version: "17.0.0"
				    repository: "https://charts.example.com"
				""");
		ChartLock resolved = ChartLock.builder()
			.dependencies(List.of(LockDependency.builder().name("redis").version("17.0.0").build()))
			.build();
		when(this.resolver.resolveDependencies(any(), any(), any())).thenReturn(resolved);

		ArgumentCaptor<List<String>> tags = ArgumentCaptor.forClass(List.class);

		this.action.update(tempDir.toFile(), List.of("db"), false);
		this.action.update(tempDir.toFile(), List.of(), false);

		verify(this.resolver, org.mockito.Mockito.times(2)).resolveDependencies(any(), any(), tags.capture());
		assertEquals(List.of("db"), tags.getAllValues().get(0));
		assertNull(tags.getAllValues().get(1), "empty tags collapse to null");
	}

}
