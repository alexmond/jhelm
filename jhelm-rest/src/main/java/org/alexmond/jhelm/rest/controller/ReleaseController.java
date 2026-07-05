package org.alexmond.jhelm.rest.controller;

import java.io.IOException;
import jakarta.validation.Valid;

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
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.UpgradeOptions;
import org.alexmond.jhelm.core.action.UpgradeValueStrategy;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.util.HookParser;
import org.alexmond.jhelm.rest.NotFoundException;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.security.MutatingOperation;
import org.alexmond.jhelm.rest.dto.HelmHookDto;
import org.alexmond.jhelm.rest.dto.InstallRequest;
import org.alexmond.jhelm.rest.dto.InstallUploadRequest;
import org.alexmond.jhelm.rest.dto.ResourceStatusDto;
import org.alexmond.jhelm.rest.dto.RollbackRequest;
import org.alexmond.jhelm.rest.dto.TestResultDto;
import org.alexmond.jhelm.rest.dto.UpgradeRequest;
import org.alexmond.jhelm.rest.dto.UpgradeUploadRequest;
import org.alexmond.jhelm.rest.util.ChartSourceResolver;
import org.alexmond.jhelm.rest.util.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.alexmond.jhelm.core.util.ValuesOverrides;

/**
 * REST endpoints for the Helm release lifecycle: install, upgrade, rollback, and
 * uninstall, plus list/status/history queries against a Kubernetes cluster.
 */
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

	private final RepoManager repoManager;

	private final JhelmRestProperties properties;

	/**
	 * Creates the controller with the release lifecycle actions it delegates to.
	 * @param listAction lists releases
	 * @param statusAction reports release and resource status
	 * @param getAction reads release values, manifest and notes
	 * @param historyAction lists release revisions
	 * @param installAction installs releases
	 * @param upgradeAction upgrades releases
	 * @param uninstallAction uninstalls releases
	 * @param rollbackAction rolls releases back to a previous revision
	 * @param testAction runs release test hooks
	 * @param chartLoader loads charts for install and upgrade
	 * @param repoManager pulls charts from repositories
	 * @param properties REST module configuration (temp directory, base path)
	 */
	public ReleaseController(ListAction listAction, StatusAction statusAction, GetAction getAction,
			HistoryAction historyAction, InstallAction installAction, UpgradeAction upgradeAction,
			UninstallAction uninstallAction, RollbackAction rollbackAction, TestAction testAction,
			ChartLoader chartLoader, RepoManager repoManager, JhelmRestProperties properties) {
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
		this.repoManager = repoManager;
		this.properties = properties;
	}

	/**
	 * {@code GET} - lists all Helm releases in a namespace.
	 * @param namespace the Kubernetes namespace
	 * @return the releases found in the namespace
	 */
	@GetMapping
	@Operation(summary = "List releases",
			description = "List all Helm releases in a namespace, in Helm's list -o json shape")
	public List<Map<String, Object>> list(
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace) {
		return this.listAction.list(namespace).stream().map(OutputFormat::listRow).toList();
	}

	/**
	 * {@code GET} - returns the status of a single release.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return {@code 200} with the release, or {@code 404} if it does not exist
	 */
	@GetMapping("/{name}")
	@Operation(summary = "Get release status", description = "Release in Helm's status -o json shape",
			responses = { @ApiResponse(responseCode = "200", description = "Release found"),
					@ApiResponse(responseCode = "404", description = "Release not found") })
	public ResponseEntity<Map<String, Object>> status(
			@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace) {
		return this.statusAction.status(name, namespace)
			.map(OutputFormat::release)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * {@code POST} - installs a new release from a repository chart reference.
	 * @param request the chart reference, release name, namespace, values and dry-run
	 * flag
	 * @return {@code 201} with the created release
	 * @throws IOException if the chart cannot be pulled or installed
	 */
	@PostMapping
	@MutatingOperation
	@Operation(summary = "Install a release",
			description = "Install a new Helm release from a repository chart reference")
	public ResponseEntity<Map<String, Object>> install(@Valid @RequestBody InstallRequest request) throws IOException {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-install-")) {
			Chart chart = ChartSourceResolver.fromChartRef(request.getChartRef(), request.getVersion(),
					this.repoManager, this.chartLoader, tempDir);
			Map<String, Object> values = ValuesOverrides.safeValues(request.getValues());
			Release release = this.installAction.install(InstallOptions.builder()
				.chart(chart)
				.releaseName(request.getReleaseName())
				.namespace(request.getNamespace())
				.values(values)
				.revision(1)
				.dryRun(request.isDryRun())
				.build());
			return ResponseEntity.status(HttpStatus.CREATED).body(OutputFormat.release(release));
		}
	}

	/**
	 * {@code POST} - installs a new release from an uploaded {@code .tgz} chart archive.
	 * @param chart the uploaded chart archive
	 * @param request the release name, namespace, values and dry-run flag
	 * @return {@code 201} with the created release
	 * @throws IOException if the upload cannot be extracted or installed
	 */
	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@MutatingOperation
	@Operation(summary = "Install a release from upload",
			description = "Install a new Helm release from an uploaded .tgz chart archive")
	public ResponseEntity<Map<String, Object>> installUpload(@RequestPart("chart") MultipartFile chart,
			@Valid @RequestPart("request") InstallUploadRequest request) throws IOException {
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-install-upload-")) {
			Chart loaded = ChartSourceResolver.fromUpload(chart, this.repoManager, this.chartLoader, tempDir);
			Map<String, Object> values = ValuesOverrides.safeValues(request.getValues());
			Release release = this.installAction.install(InstallOptions.builder()
				.chart(loaded)
				.releaseName(request.getReleaseName())
				.namespace(request.getNamespace())
				.values(values)
				.revision(1)
				.dryRun(request.isDryRun())
				.build());
			return ResponseEntity.status(HttpStatus.CREATED).body(OutputFormat.release(release));
		}
	}

	/**
	 * {@code PUT} - upgrades an existing release from a repository chart reference.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @param request the chart reference, version, values and dry-run flag
	 * @return the upgraded release
	 * @throws IOException if the chart cannot be pulled or the upgrade fails
	 */
	@PutMapping("/{name}")
	@MutatingOperation
	@Operation(summary = "Upgrade a release",
			description = "Upgrade an existing release from a repository chart reference")
	public Map<String, Object> upgrade(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@Valid @RequestBody UpgradeRequest request) throws IOException {
		Release current = this.getAction.getRelease(name, namespace)
			.orElseThrow(() -> new NotFoundException("Release '" + name + "' not found"));
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-upgrade-")) {
			Chart chart = ChartSourceResolver.fromChartRef(request.getChartRef(), request.getVersion(),
					this.repoManager, this.chartLoader, tempDir);
			Map<String, Object> values = ValuesOverrides.safeValues(request.getValues());
			Release upgraded = this.upgradeAction.upgrade(UpgradeOptions.builder()
				.currentRelease(current)
				.newChart(chart)
				.values(values)
				.valueStrategy(resolveStrategy(request.getValueStrategy()))
				.dryRun(request.isDryRun())
				.build());
			return OutputFormat.release(upgraded);
		}
	}

	/**
	 * {@code POST} - upgrades an existing release from an uploaded {@code .tgz} chart
	 * archive.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @param chart the uploaded chart archive
	 * @param request the values and dry-run flag
	 * @return the upgraded release
	 * @throws IOException if the upload cannot be extracted or the upgrade fails
	 */
	@PostMapping(path = "/{name}/upgrade/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@MutatingOperation
	@Operation(summary = "Upgrade a release from upload",
			description = "Upgrade an existing release from an uploaded .tgz chart archive")
	public Map<String, Object> upgradeUpload(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@RequestPart("chart") MultipartFile chart, @RequestPart("request") UpgradeUploadRequest request)
			throws IOException {
		Release current = this.getAction.getRelease(name, namespace)
			.orElseThrow(() -> new NotFoundException("Release '" + name + "' not found"));
		try (TempDir tempDir = new TempDir(this.properties.getTempDir(), "jhelm-upgrade-upload-")) {
			Chart loaded = ChartSourceResolver.fromUpload(chart, this.repoManager, this.chartLoader, tempDir);
			Map<String, Object> values = ValuesOverrides.safeValues(request.getValues());
			Release upgraded = this.upgradeAction.upgrade(UpgradeOptions.builder()
				.currentRelease(current)
				.newChart(loaded)
				.values(values)
				.valueStrategy(resolveStrategy(request.getValueStrategy()))
				.dryRun(request.isDryRun())
				.build());
			return OutputFormat.release(upgraded);
		}
	}

	/**
	 * Resolves the request's value strategy, defaulting to
	 * {@link UpgradeValueStrategy#DEFAULT} when the client omits it.
	 * @param strategy the strategy from the request, or {@code null}
	 * @return the strategy to apply, never {@code null}
	 */
	private static UpgradeValueStrategy resolveStrategy(UpgradeValueStrategy strategy) {
		return (strategy != null) ? strategy : UpgradeValueStrategy.DEFAULT;
	}

	/**
	 * {@code DELETE} - uninstalls a release.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return {@code 204} when the release is uninstalled
	 */
	@DeleteMapping("/{name}")
	@MutatingOperation
	@Operation(summary = "Uninstall a release")
	public ResponseEntity<Void> uninstall(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace) {
		this.uninstallAction.uninstall(UninstallOptions.builder().releaseName(name).namespace(namespace).build());
		return ResponseEntity.noContent().build();
	}

	/**
	 * {@code GET} - lists all revisions of a release.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return the release revisions, newest first
	 */
	@GetMapping("/{name}/history")
	@Operation(summary = "Get release history",
			description = "List all revisions of a release, in Helm's history -o json shape")
	public List<Map<String, Object>> history(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace) {
		return this.historyAction.history(name, namespace).stream().map(OutputFormat::historyRow).toList();
	}

	/**
	 * {@code POST} - rolls a release back to a previous revision.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @param request the target revision number
	 * @return {@code 204} when the rollback completes
	 */
	@PostMapping("/{name}/rollback")
	@MutatingOperation
	@Operation(summary = "Rollback a release", description = "Rollback a release to a previous revision")
	public ResponseEntity<Void> rollback(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@Valid @RequestBody RollbackRequest request) {
		this.rollbackAction.rollback(RollbackOptions.builder()
			.releaseName(name)
			.namespace(namespace)
			.revision(request.getRevision())
			.build());
		return ResponseEntity.noContent().build();
	}

	/**
	 * {@code POST} - runs the test hooks defined by a release.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @param timeoutSeconds the per-test timeout in seconds
	 * @return one result per executed test hook
	 */
	@PostMapping("/{name}/test")
	@MutatingOperation
	@Operation(summary = "Test a release", description = "Run test hooks for a release")
	public List<TestResultDto> test(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@Parameter(description = "Timeout in seconds") @RequestParam(defaultValue = "300") int timeoutSeconds) {
		return this.testAction.test(name, namespace, timeoutSeconds).stream().map(TestResultDto::from).toList();
	}

	/**
	 * {@code GET} - returns the values used by a release.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @param all {@code true} to include computed (default) values, {@code false} for
	 * only user-supplied overrides
	 * @return {@code 200} with the values YAML, or {@code 404} if the release does not
	 * exist
	 */
	@GetMapping("/{name}/values")
	@Operation(summary = "Get release values", description = "Get the values used by a release")
	public ResponseEntity<String> getValues(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace,
			@Parameter(description = "Include computed values") @RequestParam(defaultValue = "false") boolean all) {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(safeGetValues(release, all)))
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * {@code GET} - returns the rendered Kubernetes manifest of a release.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return {@code 200} with the manifest, or {@code 404} if the release does not exist
	 */
	@GetMapping("/{name}/manifest")
	@Operation(summary = "Get release manifest", description = "Get the rendered Kubernetes manifest")
	public ResponseEntity<String> getManifest(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace) {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(this.getAction.getManifest(release)))
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * {@code GET} - returns the rendered notes (NOTES.txt) of a release.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return {@code 200} with the notes, or {@code 404} if the release does not exist
	 */
	@GetMapping("/{name}/notes")
	@Operation(summary = "Get release notes")
	public ResponseEntity<String> getNotes(@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace) {
		return this.getAction.getRelease(name, namespace)
			.map((release) -> ResponseEntity.ok(this.getAction.getNotes(release)))
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * {@code GET} - lists the Helm hooks declared in a release manifest.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return {@code 200} with the hooks, or {@code 404} if the release does not exist
	 */
	@GetMapping("/{name}/hooks")
	@Operation(summary = "Get release hooks", description = "List Helm hooks defined in the release")
	public ResponseEntity<List<HelmHookDto>> getHooks(
			@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace) {
		return this.getAction.getRelease(name, namespace).map((release) -> {
			List<HelmHookDto> hooks = HookParser.parseHooks(release.getManifest())
				.stream()
				.map(HelmHookDto::from)
				.toList();
			return ResponseEntity.ok(hooks);
		}).orElse(ResponseEntity.notFound().build());
	}

	/**
	 * {@code GET} - lists the Kubernetes resources owned by a release and their
	 * readiness.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return {@code 200} with the resource statuses, or {@code 404} if the release does
	 * not exist
	 */
	@GetMapping("/{name}/resources")
	@Operation(summary = "Get resource statuses", description = "List Kubernetes resources and their status")
	public ResponseEntity<List<ResourceStatusDto>> getResources(
			@Parameter(description = "Release name") @PathVariable String name,
			@Parameter(description = "Kubernetes namespace") @RequestParam(defaultValue = "default") String namespace) {
		return this.statusAction.status(name, namespace).map((release) -> {
			List<ResourceStatusDto> statuses = safeGetResourceStatuses(release);
			return ResponseEntity.ok(statuses);
		}).orElse(ResponseEntity.notFound().build());
	}

	/**
	 * Reads release values, returning an empty string instead of failing the request.
	 * @param release the release whose values to read
	 * @param all {@code true} to include computed values
	 * @return the values YAML, or an empty string if they cannot be read
	 */
	private String safeGetValues(Release release, boolean all) {
		try {
			return this.getAction.getValues(release, all);
		}
		catch (Exception ex) {
			return "";
		}
	}

	/**
	 * Reads resource statuses, returning an empty list instead of failing the request.
	 * @param release the release whose resources to inspect
	 * @return the resource statuses, or an empty list if they cannot be read
	 */
	private List<ResourceStatusDto> safeGetResourceStatuses(Release release) {
		try {
			return this.statusAction.getResourceStatuses(release).stream().map(ResourceStatusDto::from).toList();
		}
		catch (Exception ex) {
			return Collections.emptyList();
		}
	}

}
