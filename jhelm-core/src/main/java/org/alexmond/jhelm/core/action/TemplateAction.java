package org.alexmond.jhelm.core.action;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.service.ValueEncryptor;
import org.alexmond.jhelm.core.util.RenderedManifest;
import org.alexmond.jhelm.core.util.ValuesLoader;
import org.alexmond.jhelm.core.util.ValuesProfiles;

@RequiredArgsConstructor
public class TemplateAction {

	private final Engine engine;

	private final ChartLoader chartLoader;

	@Setter
	private List<PostRenderProcessor> postRenderProcessors = List.of();

	@Setter
	private ValueEncryptor valueEncryptor = new ValueEncryptor(null, null, true);

	public String render(String chartPath, String releaseName, String namespace) {
		return render(chartPath, releaseName, namespace, new HashMap<>());
	}

	public String render(String chartPath, String releaseName, String namespace, Map<String, Object> overrides) {
		return render(chartPath, releaseName, namespace, overrides, null, List.of());
	}

	/**
	 * Renders a chart offline with an explicit {@code .Capabilities} override, mirroring
	 * {@code helm template --kube-version --api-versions}.
	 * @param chartPath path to the chart directory or archive
	 * @param releaseName the release name ({@code .Release.Name})
	 * @param namespace the release namespace
	 * @param overrides value overrides merged over the chart defaults
	 * @param kubeVersion the {@code .Capabilities.KubeVersion} override, or {@code null}
	 * for the engine default
	 * @param apiVersions extra API group/versions to advertise for
	 * {@code .Capabilities.APIVersions.Has} (additive to the default set)
	 * @return the rendered manifest
	 */
	public String render(String chartPath, String releaseName, String namespace, Map<String, Object> overrides,
			String kubeVersion, List<String> apiVersions) {
		return render(chartPath, releaseName, namespace, overrides, ValuesProfiles.none(), kubeVersion, apiVersions);
	}

	/**
	 * Renders a chart with active value profiles applied to its {@code values.yaml}
	 * (multi-document {@code spring.config.activate.on-profile} gating) and its
	 * {@code values-<profile>.yaml} sidecar files, plus an explicit {@code .Capabilities}
	 * override.
	 * @param chartPath path to the chart directory or archive
	 * @param releaseName the release name ({@code .Release.Name})
	 * @param namespace the release namespace
	 * @param overrides value overrides merged over the chart defaults
	 * @param profiles the active value profiles
	 * @param kubeVersion the {@code .Capabilities.KubeVersion} override, or {@code null}
	 * for the engine default
	 * @param apiVersions extra API group/versions for
	 * {@code .Capabilities.APIVersions.Has}
	 * @return the rendered manifest
	 */
	public String render(String chartPath, String releaseName, String namespace, Map<String, Object> overrides,
			ValuesProfiles profiles, String kubeVersion, List<String> apiVersions) {
		return render(chartPath, releaseName, namespace, overrides, profiles, kubeVersion, apiVersions, false, false);
	}

	/**
	 * Renders a chart with the full set of {@code helm template} controls: active value
	 * profiles, an explicit {@code .Capabilities} override, the install/upgrade posture
	 * ({@code .Release.IsInstall}/{@code .Release.IsUpgrade}), and optional inclusion of
	 * the chart's un-templated {@code crds/} manifests.
	 * @param chartPath path to the chart directory or archive
	 * @param releaseName the release name ({@code .Release.Name})
	 * @param namespace the release namespace
	 * @param overrides value overrides merged over the chart defaults
	 * @param profiles the active value profiles
	 * @param kubeVersion the {@code .Capabilities.KubeVersion} override, or {@code null}
	 * for the engine default
	 * @param apiVersions extra API group/versions for
	 * {@code .Capabilities.APIVersions.Has}
	 * @param isUpgrade render as an upgrade ({@code .Release.IsUpgrade=true},
	 * {@code IsInstall=false}) instead of the default install posture
	 * @param includeCrds prepend the chart's {@code crds/} manifests to the output (Helm
	 * excludes them from {@code template} by default)
	 * @return the rendered manifest
	 */
	public String render(String chartPath, String releaseName, String namespace, Map<String, Object> overrides,
			ValuesProfiles profiles, String kubeVersion, List<String> apiVersions, boolean isUpgrade,
			boolean includeCrds) {
		Chart chart = this.chartLoader.load(new File(chartPath), profiles);

		Map<String, Object> values = new HashMap<>(chart.getValues());
		ValuesLoader.deepMerge(values, overrides);
		this.valueEncryptor.decryptValues(values);

		ReleaseContext releaseContext = ReleaseContext.builder()
			.name(releaseName)
			.namespace(namespace)
			.install(!isUpgrade)
			.upgrade(isUpgrade)
			.revision(1)
			.build();

		String manifest = engine.render(chart, values, releaseContext, new Capabilities(kubeVersion, apiVersions));
		if (includeCrds) {
			manifest = renderCrds(chart) + manifest;
		}

		for (PostRenderProcessor processor : postRenderProcessors) {
			try {
				manifest = processor.process(manifest);
			}
			catch (Exception ex) {
				throw new JhelmException("Post-render processor failed", ex);
			}
		}

		return manifest;
	}

	/**
	 * Renders a chart and applies the {@code helm template} manifest-level controls in
	 * one call, so REST and MCP callers get the same behaviour as the CLI without
	 * repeating the post-processing: the install/upgrade posture, CRD inclusion, dropping
	 * test hooks, and selecting specific templates.
	 * @param chartPath path to the chart directory or archive
	 * @param releaseName the release name ({@code .Release.Name})
	 * @param namespace the release namespace
	 * @param overrides value overrides merged over the chart defaults
	 * @param isUpgrade render with {@code .Release.IsUpgrade=true} instead of install
	 * posture
	 * @param includeCrds prepend the chart's {@code crds/} manifests
	 * @param skipTests drop documents carrying a {@code helm.sh/hook: test} annotation
	 * @param showOnly keep only documents from these template paths (empty/{@code null} =
	 * all); errors if a requested template matches nothing
	 * @return the rendered, filtered manifest
	 */
	public String renderWithControls(String chartPath, String releaseName, String namespace,
			Map<String, Object> overrides, boolean isUpgrade, boolean includeCrds, boolean skipTests,
			List<String> showOnly) {
		String manifest = render(chartPath, releaseName, namespace, overrides, ValuesProfiles.none(), null, List.of(),
				isUpgrade, includeCrds);
		if (skipTests) {
			manifest = RenderedManifest.skipTests(manifest);
		}
		if (showOnly != null && !showOnly.isEmpty()) {
			manifest = RenderedManifest.showOnly(manifest, showOnly);
		}
		return manifest;
	}

	// Emits the chart's crds/ manifests as un-templated documents, each with a Helm-style
	// `# Source: <chart>/crds/<file>` marker, matching `helm template --include-crds`.
	// CRDs are
	// raw YAML in Helm — they are not run through the template engine.
	private String renderCrds(Chart chart) {
		StringBuilder sb = new StringBuilder();
		String chartName = chart.getMetadata().getName();
		for (Chart.Crd crd : chart.getCrds()) {
			String data = crd.getData().strip();
			if (data.isEmpty()) {
				continue;
			}
			sb.append("# Source: ").append(chartName).append("/crds/").append(crd.getName()).append('\n');
			sb.append(data).append("\n---\n");
		}
		return sb.toString();
	}

}
