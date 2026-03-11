package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.dto.ChartVersionDto;
import org.alexmond.jhelm.rest.dto.OciPushRequest;
import org.alexmond.jhelm.rest.dto.PullRequest;
import org.alexmond.jhelm.rest.dto.RepoAddRequest;
import org.alexmond.jhelm.rest.dto.RepoDto;
import org.alexmond.jhelm.rest.util.ChartArchiveUtil;
import org.alexmond.jhelm.rest.util.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${jhelm.rest.base-path:/api/v1}/repos")
@Tag(name = "Repositories", description = "Manage chart repositories")
public class RepoController {

	private static final MediaType APPLICATION_GZIP = MediaType.parseMediaType("application/gzip");

	private final RepoManager repoManager;

	private final JhelmRestProperties properties;

	public RepoController(RepoManager repoManager, JhelmRestProperties properties) {
		this.repoManager = repoManager;
		this.properties = properties;
	}

	@PostMapping
	@Operation(summary = "Add a repository")
	public ResponseEntity<Void> addRepo(@RequestBody RepoAddRequest request) throws Exception {
		if (request.getName() == null || request.getName().isBlank()) {
			throw new IllegalArgumentException("name is required");
		}
		if (request.getUrl() == null || request.getUrl().isBlank()) {
			throw new IllegalArgumentException("url is required");
		}
		this.repoManager.addRepo(request.getName(), request.getUrl());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@GetMapping
	@Operation(summary = "List repositories")
	public List<RepoDto> listRepos() throws Exception {
		RepositoryConfig config = this.repoManager.loadConfig();
		if (config.getRepositories() == null) {
			return Collections.emptyList();
		}
		return config.getRepositories().stream().map(RepoDto::from).toList();
	}

	@DeleteMapping("/{name}")
	@Operation(summary = "Remove a repository")
	public ResponseEntity<Void> removeRepo(@Parameter(description = "Repository name") @PathVariable String name)
			throws Exception {
		this.repoManager.removeRepo(name);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{name}/update")
	@Operation(summary = "Update repository index")
	public ResponseEntity<Void> updateRepo(@Parameter(description = "Repository name") @PathVariable String name)
			throws Exception {
		this.repoManager.updateRepo(name);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/{name}/charts/{chart}/versions")
	@Operation(summary = "List chart versions", description = "List available versions of a chart in a repository")
	public List<ChartVersionDto> listVersions(@Parameter(description = "Repository name") @PathVariable String name,
			@Parameter(description = "Chart name") @PathVariable String chart) throws Exception {
		return this.repoManager.getChartVersions(name, chart).stream().map(ChartVersionDto::from).toList();
	}

	@PostMapping("/pull")
	@Operation(summary = "Pull a chart",
			description = "Pull a chart from a repository or OCI registry and return it as a .tgz archive")
	public ResponseEntity<byte[]> pull(@RequestBody PullRequest request) throws Exception {
		if (request.getChart() == null || request.getChart().isBlank()) {
			throw new IllegalArgumentException("chart is required");
		}
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-pull-")) {
			this.repoManager.pull(request.getChart(), request.getVersion(), tempDir.path().toString());
			String fileName = resolveFileName(request.getChart(), request.getVersion());
			File[] files = tempDir.path().toFile().listFiles();
			if (files != null && files.length == 1 && files[0].getName().endsWith(".tgz")) {
				byte[] tgz = Files.readAllBytes(files[0].toPath());
				return ResponseEntity.ok()
					.contentType(APPLICATION_GZIP)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + files[0].getName() + "\"")
					.body(tgz);
			}
			Path chartDir = findSingleSubdir(tempDir.path());
			String dirName = chartDir.getFileName().toString();
			byte[] tgz = ChartArchiveUtil.toTgzBytes(chartDir, dirName);
			return ResponseEntity.ok()
				.contentType(APPLICATION_GZIP)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.body(tgz);
		}
	}

	@PostMapping("/update-all")
	@Operation(summary = "Update all repositories", description = "Update the index of all configured repositories")
	public ResponseEntity<Void> updateAll() throws Exception {
		this.repoManager.updateAll();
		return ResponseEntity.ok().build();
	}

	@PostMapping("/oci/push")
	@Operation(summary = "Push to OCI registry", description = "Push a chart to an OCI registry")
	public ResponseEntity<Void> pushOci(@RequestBody OciPushRequest request) throws Exception {
		if (request.getChartTgzPath() == null || request.getChartTgzPath().isBlank()) {
			throw new IllegalArgumentException("chartTgzPath is required");
		}
		if (request.getOciUrl() == null || request.getOciUrl().isBlank()) {
			throw new IllegalArgumentException("ociUrl is required");
		}
		this.repoManager.pushOci(request.getChartTgzPath(), request.getOciUrl());
		return ResponseEntity.ok().build();
	}

	private static String resolveFileName(String chart, String version) {
		String chartName = chart;
		if (chart.startsWith("oci://")) {
			return RepoManager.deriveOciFileName(chart);
		}
		if (chartName.contains("/")) {
			chartName = chartName.substring(chartName.lastIndexOf('/') + 1);
		}
		if (chartName.contains(":")) {
			chartName = chartName.substring(0, chartName.lastIndexOf(':'));
		}
		String v = (version != null) ? version : "latest";
		return chartName + "-" + v + ".tgz";
	}

	private static Path findSingleSubdir(Path parent) throws IOException {
		try (var stream = Files.list(parent)) {
			return stream.filter(Files::isDirectory).findFirst().orElse(parent);
		}
	}

}
