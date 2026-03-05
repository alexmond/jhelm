package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.util.HookParser;
import org.alexmond.jhelm.rest.dto.HelmHookDto;
import org.alexmond.jhelm.rest.dto.InstallRequest;
import org.alexmond.jhelm.rest.dto.ReleaseDto;
import org.alexmond.jhelm.rest.dto.ResourceStatusDto;
import org.alexmond.jhelm.rest.dto.RollbackRequest;
import org.alexmond.jhelm.rest.dto.TestResultDto;
import org.alexmond.jhelm.rest.dto.UpgradeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${jhelm.rest.base-path:/api/v1}/releases")
@Tag(name = "Releases", description = "Manage Helm releases")
public class ReleaseController {

	private final ListAction listAction;

	private final StatusAction statusAction;

	private final GetAction getAction;

	private final HistoryAction historyAction;

	private final InstallAction installAction;

	private final UpgradeAction upgradeAction;

	private final UninstallAction uninstallAction;

	private final RollbackAction rollbackAction;

	private final TestAction testAction;

	private final ChartLoader chartLoader;

	public ReleaseController(ListAction listAction, StatusAction statusAction, GetAction getAction,
			HistoryAction historyAction, InstallAction installAction, UpgradeAction upgradeAction,
			UninstallAction uninstallAction, RollbackAction rollbackAction, TestAction testAction,
			ChartLoader chartLoader) {
		this.listAction = listAction;
		this.statusAction = statusAction;
		this.getAction = getAction;
		this.historyAction = historyAction;
		this.installAction = installAction;
		this.upgradeAction = upgradeAction;
		this.uninstallAction = uninstallAction;
		this.rollbackAction = rollbackAction;
		this.testAction = testAction;
		this.chartLoader = chartLoader;
	}

	@GetMapping
	@Operation(summary = "List releases", description = "List all Helm releases in a namespace")
	public List<ReleaseDto> list(
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		return this.listAction.list(namespace).stream().map(ReleaseDto::from).toList();
	}

	@GetMapping("/{name}")
	@Operation(summary = "Get release status",
			responses = { @ApiResponse(responseCode = "200", description = "Release found"),
					@ApiResponse(responseCode = "404", description = "Release not found") })
	public ResponseEntity<ReleaseDto> status(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		return this.statusAction.status(name, namespace)
			.map(ReleaseDto::from)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	@Operation(summary = "Install a release", description = "Install a new Helm release from a chart")
	public ResponseEntity<ReleaseDto> install(@RequestBody InstallRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		if (request.getReleaseName() == null || request.getReleaseName().isBlank()) {
			throw new IllegalArgumentException("releaseName is required");
		}
		Chart chart = this.chartLoader.load(new File(request.getChartPath()));
		Map<String, Object> values = (request.getValues() != null) ? request.getValues() : Map.of();
		Release release = this.installAction.install(chart, request.getReleaseName(), request.getNamespace(), values, 1,
				request.isDryRun());
		return ResponseEntity.status(HttpStatus.CREATED).body(ReleaseDto.from(release));
	}

	@PutMapping("/{name}")
	@Operation(summary = "Upgrade a release", description = "Upgrade an existing release to a new chart version")
	public ReleaseDto upgrade(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@RequestBody UpgradeRequest request) throws Exception {
		if (request.getChartPath() == null || request.getChartPath().isBlank()) {
			throw new IllegalArgumentException("chartPath is required");
		}
		Release current = this.getAction.getRelease(name, namespace)
			.orElseThrow(() -> new IllegalArgumentException("Release '" + name + "' not found"));
		Chart chart = this.chartLoader.load(new File(request.getChartPath()));
		Map<String, Object> values = (request.getValues() != null) ? request.getValues() : Map.of();
		Release upgraded = this.upgradeAction.upgrade(current, chart, values, request.isDryRun());
		return ReleaseDto.from(upgraded);
	}

	@DeleteMapping("/{name}")
	@Operation(summary = "Uninstall a release")
	public ResponseEntity<Void> uninstall(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		this.uninstallAction.uninstall(name, namespace);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{name}/history")
	@Operation(summary = "Get release history", description = "List all revisions of a release")
	public List<ReleaseDto> history(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		return this.historyAction.history(name, namespace).stream().map(ReleaseDto::from).toList();
	}

	@PostMapping("/{name}/rollback")
	@Operation(summary = "Rollback a release", description = "Rollback a release to a previous revision")
	public ResponseEntity<Void> rollback(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@RequestBody RollbackRequest request) throws Exception {
		this.rollbackAction.rollback(name, namespace, request.getRevision());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{name}/test")
	@Operation(summary = "Test a release", description = "Run test hooks for a release")
	public List<TestResultDto> test(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@Parameter(description = "Timeout in seconds") @RequestParam(defaultValue = "300") int timeoutSeconds)
			throws Exception {
		return this.testAction.test(name, namespace, timeoutSeconds).stream().map(TestResultDto::from).toList();
	}

	@GetMapping("/{name}/values")
	@Operation(summary = "Get release values", description = "Get the values used by a release")
	public ResponseEntity<String> getValues(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@Parameter(description = "Include computed values") @RequestParam(defaultValue = "false") boolean all)
			throws Exception {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(safeGetValues(release, all)))
			.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{name}/manifest")
	@Operation(summary = "Get release manifest", description = "Get the rendered Kubernetes manifest")
	public ResponseEntity<String> getManifest(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(this.getAction.getManifest(release)))
			.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{name}/notes")
	@Operation(summary = "Get release notes")
	public ResponseEntity<String> getNotes(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(this.getAction.getNotes(release)))
			.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{name}/hooks")
	@Operation(summary = "Get release hooks", description = "List Helm hooks defined in the release")
	public ResponseEntity<List<HelmHookDto>> getHooks(
			@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		return this.getAction.getRelease(name, namespace).map((release) -> {
			List<HelmHookDto> hooks = HookParser.parseHooks(release.getManifest())
				.stream()
				.map(HelmHookDto::from)
				.toList();
			return ResponseEntity.ok(hooks);
		}).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{name}/resources")
	@Operation(summary = "Get resource statuses", description = "List Kubernetes resources and their status")
	public ResponseEntity<List<ResourceStatusDto>> getResources(
			@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		return this.statusAction.status(name, namespace).map((release) -> {
			List<ResourceStatusDto> statuses = safeGetResourceStatuses(release);
			return ResponseEntity.ok(statuses);
		}).orElse(ResponseEntity.notFound().build());
	}

	private String safeGetValues(Release release, boolean all) {
		try {
			return this.getAction.getValues(release, all);
		}
		catch (Exception ex) {
			return "";
		}
	}

	private List<ResourceStatusDto> safeGetResourceStatuses(Release release) {
		try {
			return this.statusAction.getResourceStatuses(release).stream().map(ResourceStatusDto::from).toList();
		}
		catch (Exception ex) {
			return Collections.emptyList();
		}
	}

}
