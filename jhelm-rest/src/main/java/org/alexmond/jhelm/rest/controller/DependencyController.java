package org.alexmond.jhelm.rest.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartLock;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.dto.DependencyResolveRequest;
import org.alexmond.jhelm.rest.util.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${jhelm.rest.base-path:/api/v1}/dependencies")
@Tag(name = "Dependencies", description = "Manage chart dependencies")
public class DependencyController {

	private static final MediaType TEXT_YAML = MediaType.parseMediaType("text/yaml");

	private final DependencyResolver dependencyResolver;

	private final ChartLoader chartLoader;

	private final RepoManager repoManager;

	private final JhelmRestProperties properties;

	public DependencyController(DependencyResolver dependencyResolver, ChartLoader chartLoader, RepoManager repoManager,
			JhelmRestProperties properties) {
		this.dependencyResolver = dependencyResolver;
		this.chartLoader = chartLoader;
		this.repoManager = repoManager;
		this.properties = properties;
	}

	@PostMapping("/resolve")
	@Operation(summary = "Resolve dependencies",
			description = "Resolve chart dependency versions from a repository chart reference and return the Chart.lock content")
	public ResponseEntity<String> resolve(@RequestBody DependencyResolveRequest request) throws Exception {
		if (request.getChartRef() == null || request.getChartRef().isBlank()) {
			throw new IllegalArgumentException("chartRef is required");
		}
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-dep-resolve-")) {
			this.repoManager.pull(request.getChartRef(), request.getVersion(), tempDir.path().toString());
			Chart chart = this.chartLoader.load(findChartDir(tempDir.path()).toFile());
			Map<String, Object> values = (chart.getValues() != null) ? chart.getValues() : Map.of();
			ChartLock lock = this.dependencyResolver.resolveDependencies(chart.getMetadata(), values, List.of());
			return ResponseEntity.ok().contentType(TEXT_YAML).body(lock.toYaml());
		}
	}

	private static Path findChartDir(Path parent) throws IOException {
		try (var stream = Files.list(parent)) {
			return stream.filter(Files::isDirectory).findFirst().orElse(parent);
		}
	}

}
