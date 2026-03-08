package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartLock;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.dto.DependencyResolveRequest;
import org.alexmond.jhelm.rest.util.ChartArchiveUtil;
import org.alexmond.jhelm.rest.util.TempDir;
import org.springframework.http.HttpHeaders;
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

	private static final MediaType APPLICATION_GZIP = MediaType.parseMediaType("application/gzip");

	private static final MediaType TEXT_YAML = MediaType.parseMediaType("text/yaml");

	private final DependencyResolver dependencyResolver;

	private final ChartLoader chartLoader;

	private final JhelmRestProperties properties;

	public DependencyController(DependencyResolver dependencyResolver, ChartLoader chartLoader,
			JhelmRestProperties properties) {
		this.dependencyResolver = dependencyResolver;
		this.chartLoader = chartLoader;
		this.properties = properties;
	}

	@PostMapping("/resolve")
	@Operation(summary = "Resolve dependencies",
			description = "Resolve chart dependency versions and return the Chart.lock content")
	public ResponseEntity<String> resolve(@RequestBody DependencyResolveRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		Chart chart = this.chartLoader.load(new File(request.getChartPath()));
		Map<String, Object> values = (chart.getValues() != null) ? chart.getValues() : Map.of();
		ChartLock lock = this.dependencyResolver.resolveDependencies(chart.getMetadata(), values, List.of());
		return ResponseEntity.ok().contentType(TEXT_YAML).body(lock.toYaml());
	}

	@PostMapping("/download")
	@Operation(summary = "Download dependencies",
			description = "Download resolved chart dependencies and return them as a .tgz archive")
	public ResponseEntity<byte[]> download(@RequestBody DependencyResolveRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		File chartDir = new File(request.getChartPath());
		ChartLock lock = ChartLock.fromFile(chartDir);
		if (lock == null) {
			throw new IllegalArgumentException("No Chart.lock found. Run resolve first.");
		}
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-dep-download-")) {
			this.dependencyResolver.downloadDependencies(tempDir.path().toFile(), lock.getDependencies());
			byte[] tgz = ChartArchiveUtil.toTgzBytes(tempDir.path(), "dependencies");
			return ResponseEntity.ok()
				.contentType(APPLICATION_GZIP)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dependencies.tgz\"")
				.body(tgz);
		}
	}

}
