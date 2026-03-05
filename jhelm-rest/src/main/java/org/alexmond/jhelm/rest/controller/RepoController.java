package org.alexmond.jhelm.rest.controller;

import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.rest.dto.ChartVersionDto;
import org.alexmond.jhelm.rest.dto.PullRequest;
import org.alexmond.jhelm.rest.dto.RepoAddRequest;
import org.alexmond.jhelm.rest.dto.RepoDto;
import org.springframework.http.HttpStatus;
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

	private final RepoManager repoManager;

	public RepoController(RepoManager repoManager) {
		this.repoManager = repoManager;
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

	@PostMapping("/{name}/charts/{chart}/pull")
	@Operation(summary = "Pull a chart", description = "Download a chart from a repository")
	public ResponseEntity<Void> pullChart(@Parameter(description = "Repository name") @PathVariable String name,
			@Parameter(description = "Chart name") @PathVariable String chart, @RequestBody PullRequest request)
			throws Exception {
		if (request.getDestination() == null || request.getDestination().isBlank()) {
			throw new IllegalArgumentException("destination is required");
		}
		this.repoManager.pull(chart, name, request.getVersion(), request.getDestination());
		return ResponseEntity.ok().build();
	}

}
