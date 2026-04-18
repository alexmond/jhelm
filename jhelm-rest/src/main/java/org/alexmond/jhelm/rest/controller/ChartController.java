package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.dto.CreateRequest;

import org.alexmond.jhelm.rest.dto.TemplateRequest;
import org.alexmond.jhelm.rest.dto.TemplateUploadRequest;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("${jhelm.rest.base-path:/api/v1}/charts")
@Tag(name = "Charts", description = "Chart operations: template, create, show")
public class ChartController {

	private static final MediaType APPLICATION_GZIP = MediaType.parseMediaType("application/gzip");

	private final TemplateAction templateAction;

	private final CreateAction createAction;

	private final ShowAction showAction;

	private final RepoManager repoManager;

	private final JhelmRestProperties properties;

	public ChartController(TemplateAction templateAction, CreateAction createAction, ShowAction showAction,
			RepoManager repoManager, JhelmRestProperties properties) {
		this.templateAction = templateAction;
		this.createAction = createAction;
		this.showAction = showAction;
		this.repoManager = repoManager;
		this.properties = properties;
	}

	@PostMapping("/template")
	@Operation(summary = "Render templates",
			description = "Render chart templates from a repository chart reference with optional value overrides")
	public ResponseEntity<String> template(@RequestBody TemplateRequest request) throws Exception {
		if (request.getChartRef() == null || request.getChartRef().isBlank()) {
			throw new IllegalArgumentException("chartRef is required");
		}
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-template-")) {
			String chartPath = pullChart(request.getChartRef(), request.getVersion(), tempDir);
			Map<String, Object> values = ValuesOverrides.safeValues(request.getValues());
			String manifest = this.templateAction.render(chartPath, request.getReleaseName(), request.getNamespace(),
					values);
			return ResponseEntity.ok(manifest);
		}
	}

	@PostMapping(path = "/template/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Render templates from upload",
			description = "Render chart templates from an uploaded .tgz chart archive")
	public ResponseEntity<String> templateUpload(@RequestPart("chart") MultipartFile chart,
			@RequestPart("request") TemplateUploadRequest request) throws Exception {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-template-upload-")) {
			File tgzFile = tempDir.path().resolve("upload.tgz").toFile();
			chart.transferTo(tgzFile);
			this.repoManager.untar(tgzFile, tempDir.path().toFile());
			String chartPath = ChartLoader.findChartDir(tempDir.path()).toString();
			Map<String, Object> values = ValuesOverrides.safeValues(request.getValues());
			String manifest = this.templateAction.render(chartPath, request.getReleaseName(), request.getNamespace(),
					values);
			return ResponseEntity.ok(manifest);
		}
	}

	@PostMapping("/create")
	@Operation(summary = "Create a chart",
			description = "Scaffold a new chart and return it as a .tgz archive download")
	public ResponseEntity<byte[]> create(@RequestBody CreateRequest request) throws Exception {
		if (request.getName() == null || request.getName().isBlank()) {
			throw new IllegalArgumentException("name is required");
		}
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-create-")) {
			Path chartPath = tempDir.sandboxedResolve(request.getName());
			this.createAction.create(chartPath);
			byte[] tgz = ChartArchiveUtil.toTgzBytes(chartPath, request.getName());
			return ResponseEntity.ok()
				.contentType(APPLICATION_GZIP)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + request.getName() + ".tgz\"")
				.body(tgz);
		}
	}

	@GetMapping("/show")
	@Operation(summary = "Show chart info", description = "Show all chart information: metadata, values, README, CRDs")
	public ResponseEntity<String> showAll(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version) throws Exception {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showAll(chartPath));
		}
	}

	@GetMapping("/show/values")
	@Operation(summary = "Show chart values", description = "Show the default values.yaml")
	public ResponseEntity<String> showValues(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version) throws Exception {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showValues(chartPath));
		}
	}

	@GetMapping("/show/readme")
	@Operation(summary = "Show chart README")
	public ResponseEntity<String> showReadme(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version) throws Exception {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showReadme(chartPath));
		}
	}

	@GetMapping("/show/chart")
	@Operation(summary = "Show chart metadata", description = "Show the Chart.yaml metadata")
	public ResponseEntity<String> showChart(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version) throws Exception {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showChart(chartPath));
		}
	}

	@GetMapping("/show/crds")
	@Operation(summary = "Show chart CRDs", description = "Show Custom Resource Definitions bundled with the chart")
	public ResponseEntity<String> showCrds(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version) throws Exception {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showCrds(chartPath));
		}
	}

	private String pullChart(String chartRef, String version, TempDir tempDir) throws IOException {
		this.repoManager.pull(chartRef, version, tempDir.path().toString());
		return ChartLoader.findChartDir(tempDir.path()).toString();
	}

}
