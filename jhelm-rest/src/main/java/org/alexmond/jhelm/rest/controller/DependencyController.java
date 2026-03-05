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
import org.alexmond.jhelm.rest.dto.DependencyResolveRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${jhelm.rest.base-path:/api/v1}/dependencies")
@Tag(name = "Dependencies", description = "Manage chart dependencies")
public class DependencyController {

	private final DependencyResolver dependencyResolver;

	private final ChartLoader chartLoader;

	public DependencyController(DependencyResolver dependencyResolver, ChartLoader chartLoader) {
		this.dependencyResolver = dependencyResolver;
		this.chartLoader = chartLoader;
	}

	@PostMapping("/resolve")
	@Operation(summary = "Resolve dependencies", description = "Resolve chart dependency versions")
	public ResponseEntity<Void> resolve(@RequestBody DependencyResolveRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		Chart chart = this.chartLoader.load(new File(request.getChartPath()));
		Map<String, Object> values = (chart.getValues() != null) ? chart.getValues() : Map.of();
		ChartLock lock = this.dependencyResolver.resolveDependencies(chart.getMetadata(), values, List.of());
		lock.toFile(new File(request.getChartPath()));
		return ResponseEntity.ok().build();
	}

	@PostMapping("/download")
	@Operation(summary = "Download dependencies", description = "Download resolved chart dependencies")
	public ResponseEntity<Void> download(@RequestBody DependencyResolveRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		File chartDir = new File(request.getChartPath());
		ChartLock lock = ChartLock.fromFile(chartDir);
		if (lock == null) {
			throw new IllegalArgumentException("No Chart.lock found. Run resolve first.");
		}
		this.dependencyResolver.downloadDependencies(chartDir, lock.getDependencies());
		return ResponseEntity.ok().build();
	}

}
