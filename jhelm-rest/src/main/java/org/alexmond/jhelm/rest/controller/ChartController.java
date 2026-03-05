package org.alexmond.jhelm.rest.controller;

import java.io.File;
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
import org.alexmond.jhelm.rest.dto.CreateRequest;
import org.alexmond.jhelm.rest.dto.LintRequest;
import org.alexmond.jhelm.rest.dto.LintResultDto;
import org.alexmond.jhelm.rest.dto.PackageRequest;
import org.alexmond.jhelm.rest.dto.PackageResultDto;
import org.alexmond.jhelm.rest.dto.TemplateRequest;
import org.alexmond.jhelm.rest.dto.VerifyRequest;
import org.springframework.http.HttpStatus;
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

	private final TemplateAction templateAction;

	private final LintAction lintAction;

	private final CreateAction createAction;

	private final PackageAction packageAction;

	private final VerifyAction verifyAction;

	private final ShowAction showAction;

	public ChartController(TemplateAction templateAction, LintAction lintAction, CreateAction createAction,
			PackageAction packageAction, VerifyAction verifyAction, ShowAction showAction) {
		this.templateAction = templateAction;
		this.lintAction = lintAction;
		this.createAction = createAction;
		this.packageAction = packageAction;
		this.verifyAction = verifyAction;
		this.showAction = showAction;
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
	@Operation(summary = "Create a chart", description = "Scaffold a new chart directory")
	public ResponseEntity<Void> create(@RequestBody CreateRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		this.createAction.create(Path.of(request.getChartPath()));
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/package")
	@Operation(summary = "Package a chart", description = "Package a chart directory into a .tgz archive")
	public PackageResultDto packageChart(@RequestBody PackageRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		if (request.getDestination() != null) {
			this.packageAction.setDestination(new File(request.getDestination()));
		}
		File archive = this.packageAction.packageChart(request.getChartPath());
		return PackageResultDto.builder().archivePath(archive.getAbsolutePath()).build();
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

}
