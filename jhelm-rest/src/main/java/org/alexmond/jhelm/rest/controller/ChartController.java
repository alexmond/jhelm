package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.PackageAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.dto.CreateRequest;
import org.alexmond.jhelm.rest.dto.LintRequest;
import org.alexmond.jhelm.rest.dto.LintResultDto;
import org.alexmond.jhelm.rest.dto.PackageRequest;
import org.alexmond.jhelm.rest.dto.TemplateRequest;
import org.alexmond.jhelm.rest.dto.VerifyRequest;
import org.alexmond.jhelm.rest.util.ChartArchiveUtil;
import org.alexmond.jhelm.rest.util.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${jhelm.rest.base-path:/api/v1}/charts")
@Tag(name = "Charts", description = "Chart operations: template, lint, create, package, verify, show")
public class ChartController {

	private static final MediaType APPLICATION_GZIP = MediaType.parseMediaType("application/gzip");

	private final TemplateAction templateAction;

	private final LintAction lintAction;

	private final CreateAction createAction;

	private final PackageAction packageAction;

	private final VerifyAction verifyAction;

	private final ShowAction showAction;

	private final JhelmRestProperties properties;

	public ChartController(TemplateAction templateAction, LintAction lintAction, CreateAction createAction,
			PackageAction packageAction, VerifyAction verifyAction, ShowAction showAction,
			JhelmRestProperties properties) {
		this.templateAction = templateAction;
		this.lintAction = lintAction;
		this.createAction = createAction;
		this.packageAction = packageAction;
		this.verifyAction = verifyAction;
		this.showAction = showAction;
		this.properties = properties;
	}

	@PostMapping("/template")
	@Operation(summary = "Render templates", description = "Render chart templates with optional value overrides")
	public ResponseEntity<String> template(@RequestBody TemplateRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		Map<String, Object> values = (request.getValues() != null) ? request.getValues() : Map.of();
		String manifest = this.templateAction.render(request.getChartPath(), request.getReleaseName(),
				request.getNamespace(), values);
		return ResponseEntity.ok(manifest);
	}

	@PostMapping("/lint")
	@Operation(summary = "Lint a chart", description = "Validate a chart for issues and best practices")
	public LintResultDto lint(@RequestBody LintRequest request) {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		Map<String, Object> values = (request.getValues() != null) ? request.getValues() : Map.of();
		LintAction.LintResult result = this.lintAction.lint(request.getChartPath(), values, request.isStrict());
		return LintResultDto.from(result);
	}

	@PostMapping("/create")
	@Operation(summary = "Create a chart",
			description = "Scaffold a new chart and return it as a .tgz archive download")
	public ResponseEntity<byte[]> create(@RequestBody CreateRequest request) throws Exception {
		if (request.getName() == null || request.getName().isBlank()) {
			throw new IllegalArgumentException("name is required");
		}
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-create-")) {
			Path chartPath = tempDir.path().resolve(request.getName());
			this.createAction.create(chartPath);
			byte[] tgz = ChartArchiveUtil.toTgzBytes(chartPath, request.getName());
			return ResponseEntity.ok()
				.contentType(APPLICATION_GZIP)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + request.getName() + ".tgz\"")
				.body(tgz);
		}
	}

	@PostMapping("/package")
	@Operation(summary = "Package a chart",
			description = "Package a chart directory into a .tgz archive and return it as a download")
	public ResponseEntity<byte[]> packageChart(@RequestBody PackageRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-package-")) {
			this.packageAction.setDestination(tempDir.path().toFile());
			File archive = this.packageAction.packageChart(request.getChartPath());
			byte[] tgz = Files.readAllBytes(archive.toPath());
			String fileName = archive.getName();
			return ResponseEntity.ok()
				.contentType(APPLICATION_GZIP)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.body(tgz);
		}
	}

	@PostMapping("/verify")
	@Operation(summary = "Verify a chart", description = "Verify the PGP signature of a packaged chart")
	public ResponseEntity<Void> verify(@RequestBody VerifyRequest request) throws Exception {
		if (request.getChartTgzPath() == null || request.getChartTgzPath().isBlank()) {
			throw new IllegalArgumentException("chartTgzPath is required");
		}
		if (request.getKeyringPath() == null || request.getKeyringPath().isBlank()) {
			throw new IllegalArgumentException("keyringPath is required");
		}
		this.verifyAction.verify(request.getChartTgzPath(), request.getKeyringPath());
		return ResponseEntity.ok().build();
	}

	@GetMapping("/show")
	@Operation(summary = "Show chart info", description = "Show all chart information: metadata, values, README, CRDs")
	public ResponseEntity<String> showAll(
			@Parameter(description = "Path to the chart directory") @RequestParam String chartPath) throws Exception {
		return ResponseEntity.ok(this.showAction.showAll(chartPath));
	}

	@GetMapping("/show/values")
	@Operation(summary = "Show chart values", description = "Show the default values.yaml")
	public ResponseEntity<String> showValues(
			@Parameter(description = "Path to the chart directory") @RequestParam String chartPath) throws Exception {
		return ResponseEntity.ok(this.showAction.showValues(chartPath));
	}

	@GetMapping("/show/readme")
	@Operation(summary = "Show chart README")
	public ResponseEntity<String> showReadme(
			@Parameter(description = "Path to the chart directory") @RequestParam String chartPath) throws Exception {
		return ResponseEntity.ok(this.showAction.showReadme(chartPath));
	}

	@GetMapping("/show/chart")
	@Operation(summary = "Show chart metadata", description = "Show the Chart.yaml metadata")
	public ResponseEntity<String> showChart(
			@Parameter(description = "Path to the chart directory") @RequestParam String chartPath) throws Exception {
		return ResponseEntity.ok(this.showAction.showChart(chartPath));
	}

	@GetMapping("/show/crds")
	@Operation(summary = "Show chart CRDs", description = "Show Custom Resource Definitions bundled with the chart")
	public ResponseEntity<String> showCrds(
			@Parameter(description = "Path to the chart directory") @RequestParam String chartPath) throws Exception {
		return ResponseEntity.ok(this.showAction.showCrds(chartPath));
	}

}
