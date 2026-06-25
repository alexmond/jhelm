package org.alexmond.jhelm.rest.controller;

import java.io.File;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.alexmond.jhelm.core.service.ChartLoader;

/**
 * REST endpoints for managing chart repositories: adding, removing, and listing repos,
 * and searching their indexes.
 */
@RestController
@RequestMapping("${jhelm.rest.base-path:/api/v1}/repos")
@Tag(name = "Repositories", description = "Manage chart repositories")
public class RepoController {

	private static final MediaType APPLICATION_GZIP = MediaType.parseMediaType("application/gzip");

	private final RepoManager repoManager;

	private final JhelmRestProperties properties;

	/**
	 * Creates the controller with the repository manager it delegates to.
	 * @param repoManager manages chart repositories
	 * @param properties REST module configuration (temp directory, base path)
	 */
	public RepoController(RepoManager repoManager, JhelmRestProperties properties) {
		this.repoManager = repoManager;
		this.properties = properties;
	}

	/**
	 * {@code POST} - registers a new chart repository by name and URL.
	 * @param request the repository name and URL
	 * @return {@code 201} when the repository is added
	 * @throws Exception if the repository cannot be added
	 */
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

	/**
	 * {@code GET} - lists all configured chart repositories.
	 * @return the configured repositories, or an empty list when none are configured
	 * @throws Exception if the repository configuration cannot be read
	 */
	@GetMapping
	@Operation(summary = "List repositories")
	public List<RepoDto> listRepos() throws Exception {
		RepositoryConfig config = this.repoManager.loadConfig();
		if (config.getRepositories() == null) {
			return Collections.emptyList();
		}
		return config.getRepositories().stream().map(RepoDto::from).toList();
	}

	/**
	 * {@code DELETE} - removes a configured chart repository.
	 * @param name the repository name
	 * @return {@code 204} when the repository is removed
	 * @throws Exception if the repository cannot be removed
	 */
	@DeleteMapping("/{name}")
	@Operation(summary = "Remove a repository")
	public ResponseEntity<Void> removeRepo(@Parameter(description = "Repository name") @PathVariable String name)
			throws Exception {
		this.repoManager.removeRepo(name);
		return ResponseEntity.noContent().build();
	}

	/**
	 * {@code POST} - refreshes a single repository's cached index.
	 * @param name the repository name
	 * @return {@code 200} when the index is refreshed
	 * @throws Exception if the index cannot be updated
	 */
	@PostMapping("/{name}/update")
	@Operation(summary = "Update repository index")
	public ResponseEntity<Void> updateRepo(@Parameter(description = "Repository name") @PathVariable String name)
			throws Exception {
		this.repoManager.updateRepo(name);
		return ResponseEntity.ok().build();
	}

	/**
	 * {@code GET} - lists all charts available in a repository.
	 * @param name the repository name
	 * @return the charts advertised by the repository index
	 * @throws Exception if the repository index cannot be read
	 */
	@GetMapping("/{name}/charts")
	@Operation(summary = "List charts", description = "List all charts available in a repository")
	public List<ChartVersionDto> listCharts(@Parameter(description = "Repository name") @PathVariable String name)
			throws Exception {
		return this.repoManager.listCharts(name).stream().map(ChartVersionDto::from).toList();
	}

	/**
	 * {@code GET} - lists the available versions of a chart in a repository.
	 * @param name the repository name
	 * @param chart the chart name
	 * @return the known versions of the chart
	 * @throws Exception if the repository index cannot be read
	 */
	@GetMapping("/{name}/charts/{chart}/versions")
	@Operation(summary = "List chart versions", description = "List available versions of a chart in a repository")
	public List<ChartVersionDto> listVersions(@Parameter(description = "Repository name") @PathVariable String name,
			@Parameter(description = "Chart name") @PathVariable String chart) throws Exception {
		return this.repoManager.getChartVersions(name, chart).stream().map(ChartVersionDto::from).toList();
	}

	/**
	 * {@code POST} - pulls a chart from a repository or OCI registry and returns it as a
	 * {@code .tgz} archive download.
	 * @param request the chart reference and optional version
	 * @return {@code 200} with the {@code .tgz} archive as an attachment
	 * @throws Exception if the chart cannot be pulled or archived
	 */
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
			Path chartDir = ChartLoader.findChartDir(tempDir.path());
			String dirName = chartDir.getFileName().toString();
			byte[] tgz = ChartArchiveUtil.toTgzBytes(chartDir, dirName);
			return ResponseEntity.ok()
				.contentType(APPLICATION_GZIP)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.body(tgz);
		}
	}

	/**
	 * {@code POST} - refreshes the cached index of every configured repository.
	 * @return {@code 200} when all indexes are refreshed
	 * @throws Exception if any index cannot be updated
	 */
	@PostMapping("/update-all")
	@Operation(summary = "Update all repositories", description = "Update the index of all configured repositories")
	public ResponseEntity<Void> updateAll() throws Exception {
		this.repoManager.updateAll();
		return ResponseEntity.ok().build();
	}

	/**
	 * {@code POST} - pushes an uploaded {@code .tgz} chart archive to a remote OCI
	 * registry.
	 * @param chart the uploaded chart archive
	 * @param remote the target OCI registry reference
	 * @return {@code 200} when the chart is pushed
	 * @throws Exception if the upload cannot be stored or pushed
	 */
	@PostMapping(path = "/push", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Push a chart", description = "Push an uploaded .tgz chart archive to a remote registry")
	public ResponseEntity<Void> push(@RequestParam("chart") MultipartFile chart, @RequestParam("remote") String remote)
			throws Exception {
		if (remote == null || remote.isBlank()) {
			throw new IllegalArgumentException("remote is required");
		}
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-push-")) {
			Path tgzPath = tempDir.path().resolve("upload.tgz");
			chart.transferTo(tgzPath.toFile());
			this.repoManager.pushOci(tgzPath.toString(), remote);
			return ResponseEntity.ok().build();
		}
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

}
