package org.alexmond.jhelm.rest.controller;

import jakarta.validation.Valid;

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

/**
 * REST endpoints for chart-level operations: rendering templates, scaffolding new charts,
 * and inspecting chart metadata, values, and CRDs.
 */
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

	/**
	 * Creates the controller with the chart-related actions it delegates to.
	 * @param templateAction renders chart templates
	 * @param createAction scaffolds new charts
	 * @param showAction exposes chart information
	 * @param repoManager pulls charts from repositories
	 * @param properties REST module configuration (temp directory, base path)
	 */
	public ChartController(TemplateAction templateAction, CreateAction createAction, ShowAction showAction,
			RepoManager repoManager, JhelmRestProperties properties) {
		this.templateAction = templateAction;
		this.createAction = createAction;
		this.showAction = showAction;
		this.repoManager = repoManager;
		this.properties = properties;
	}

	/**
	 * {@code POST} - renders a repository chart's templates with optional value overrides
	 * and returns the combined manifest text.
	 * @param request the chart reference, version, release name, namespace and values
	 * @return {@code 200} with the rendered manifest
	 */
	@PostMapping("/template")
	@Operation(summary = "Render templates",
			description = "Render chart templates from a repository chart reference with optional value overrides")
	public ResponseEntity<String> template(@Valid @RequestBody TemplateRequest request) throws IOException {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-template-")) {
			String chartPath = pullChart(request.getChartRef(), request.getVersion(), tempDir);
			Map<String, Object> values = ValuesOverrides.safeValues(request.getValues());
			String manifest = this.templateAction.render(chartPath, request.getReleaseName(), request.getNamespace(),
					values);
			return ResponseEntity.ok(manifest);
		}
	}

	/**
	 * {@code POST} - renders templates from an uploaded {@code .tgz} chart archive and
	 * returns the combined manifest text.
	 * @param chart the uploaded chart archive
	 * @param request the release name, namespace and value overrides
	 * @return {@code 200} with the rendered manifest
	 */
	@PostMapping(path = "/template/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Render templates from upload",
			description = "Render chart templates from an uploaded .tgz chart archive")
	public ResponseEntity<String> templateUpload(@RequestPart("chart") MultipartFile chart,
			@RequestPart("request") TemplateUploadRequest request) throws IOException {
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

	/**
	 * {@code POST} - scaffolds a new chart and returns it as a {@code .tgz} archive
	 * download.
	 * @param request the name of the chart to scaffold
	 * @return {@code 200} with the {@code .tgz} archive as an attachment
	 */
	@PostMapping("/create")
	@Operation(summary = "Create a chart",
			description = "Scaffold a new chart and return it as a .tgz archive download")
	public ResponseEntity<byte[]> create(@Valid @RequestBody CreateRequest request) throws IOException {
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

	/**
	 * {@code GET} - returns all available chart information (metadata, values, README and
	 * CRDs) for a repository chart.
	 * @param chartRef the chart reference (repo/chart or {@code oci://...})
	 * @param version the optional chart version
	 * @return {@code 200} with the combined chart information
	 */
	@GetMapping("/show")
	@Operation(summary = "Show chart info", description = "Show all chart information: metadata, values, README, CRDs")
	public ResponseEntity<String> showAll(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version)
			throws IOException {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showAll(chartPath));
		}
	}

	/**
	 * {@code GET} - returns a repository chart's default {@code values.yaml}.
	 * @param chartRef the chart reference (repo/chart or {@code oci://...})
	 * @param version the optional chart version
	 * @return {@code 200} with the default values
	 */
	@GetMapping("/show/values")
	@Operation(summary = "Show chart values", description = "Show the default values.yaml")
	public ResponseEntity<String> showValues(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version)
			throws IOException {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showValues(chartPath));
		}
	}

	/**
	 * {@code GET} - returns a repository chart's README.
	 * @param chartRef the chart reference (repo/chart or {@code oci://...})
	 * @param version the optional chart version
	 * @return {@code 200} with the README content
	 */
	@GetMapping("/show/readme")
	@Operation(summary = "Show chart README")
	public ResponseEntity<String> showReadme(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version)
			throws IOException {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showReadme(chartPath));
		}
	}

	/**
	 * {@code GET} - returns a repository chart's {@code Chart.yaml} metadata.
	 * @param chartRef the chart reference (repo/chart or {@code oci://...})
	 * @param version the optional chart version
	 * @return {@code 200} with the chart metadata
	 */
	@GetMapping("/show/chart")
	@Operation(summary = "Show chart metadata", description = "Show the Chart.yaml metadata")
	public ResponseEntity<String> showChart(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version)
			throws IOException {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showChart(chartPath));
		}
	}

	/**
	 * {@code GET} - returns the Custom Resource Definitions bundled with a repository
	 * chart.
	 * @param chartRef the chart reference (repo/chart or {@code oci://...})
	 * @param version the optional chart version
	 * @return {@code 200} with the bundled CRDs
	 */
	@GetMapping("/show/crds")
	@Operation(summary = "Show chart CRDs", description = "Show Custom Resource Definitions bundled with the chart")
	public ResponseEntity<String> showCrds(
			@Parameter(description = "Chart reference (repo/chart or oci://...)") @RequestParam String chartRef,
			@Parameter(description = "Chart version") @RequestParam(required = false) String version)
			throws IOException {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-show-")) {
			String chartPath = pullChart(chartRef, version, tempDir);
			return ResponseEntity.ok(this.showAction.showCrds(chartPath));
		}
	}

	/**
	 * Pulls a chart into the temp directory and returns the path to its chart directory.
	 * @param chartRef the chart reference
	 * @param version the optional chart version
	 * @param tempDir the temporary directory to pull into
	 * @return the path to the extracted chart directory
	 * @throws IOException if the pull or directory lookup fails
	 */
	private String pullChart(String chartRef, String version, TempDir tempDir) throws IOException {
		this.repoManager.pull(chartRef, version, tempDir.path().toString());
		return ChartLoader.findChartDir(tempDir.path()).toString();
	}

}
