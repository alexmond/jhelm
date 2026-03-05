package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
	public List<ReleaseDto> list(@RequestParam(defaultValue = "default") String namespace) throws Exception {
		return this.listAction.list(namespace).stream().map(ReleaseDto::from).toList();
	}

	@GetMapping("/{name}")
	public ResponseEntity<ReleaseDto> status(@PathVariable String name,
			@RequestParam(defaultValue = "default") String namespace) throws Exception {
		return this.statusAction.status(name, namespace)
			.map(ReleaseDto::from)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
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
	public ReleaseDto upgrade(@PathVariable String name, @RequestParam(defaultValue = "default") String namespace,
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
	public ResponseEntity<Void> uninstall(@PathVariable String name,
			@RequestParam(defaultValue = "default") String namespace) throws Exception {
		this.uninstallAction.uninstall(name, namespace);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{name}/history")
	public List<ReleaseDto> history(@PathVariable String name, @RequestParam(defaultValue = "default") String namespace)
			throws Exception {
		return this.historyAction.history(name, namespace).stream().map(ReleaseDto::from).toList();
	}

	@PostMapping("/{name}/rollback")
	public ResponseEntity<Void> rollback(@PathVariable String name,
			@RequestParam(defaultValue = "default") String namespace, @RequestBody RollbackRequest request)
			throws Exception {
		this.rollbackAction.rollback(name, namespace, request.getRevision());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{name}/test")
	public List<TestResultDto> test(@PathVariable String name, @RequestParam(defaultValue = "default") String namespace,
			@RequestParam(defaultValue = "300") int timeoutSeconds) throws Exception {
		return this.testAction.test(name, namespace, timeoutSeconds).stream().map(TestResultDto::from).toList();
	}

	@GetMapping("/{name}/values")
	public ResponseEntity<String> getValues(@PathVariable String name,
			@RequestParam(defaultValue = "default") String namespace, @RequestParam(defaultValue = "false") boolean all)
			throws Exception {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(safeGetValues(release, all)))
			.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{name}/manifest")
	public ResponseEntity<String> getManifest(@PathVariable String name,
			@RequestParam(defaultValue = "default") String namespace) throws Exception {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(this.getAction.getManifest(release)))
			.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{name}/notes")
	public ResponseEntity<String> getNotes(@PathVariable String name,
			@RequestParam(defaultValue = "default") String namespace) throws Exception {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(this.getAction.getNotes(release)))
			.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{name}/hooks")
	public ResponseEntity<List<HelmHookDto>> getHooks(@PathVariable String name,
			@RequestParam(defaultValue = "default") String namespace) throws Exception {
		return this.getAction.getRelease(name, namespace).map((release) -> {
			List<HelmHookDto> hooks = HookParser.parseHooks(release.getManifest())
				.stream()
				.map(HelmHookDto::from)
				.toList();
			return ResponseEntity.ok(hooks);
		}).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{name}/resources")
	public ResponseEntity<List<ResourceStatusDto>> getResources(@PathVariable String name,
			@RequestParam(defaultValue = "default") String namespace) throws Exception {
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
