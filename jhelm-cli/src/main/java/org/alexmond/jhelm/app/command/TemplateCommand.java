package org.alexmond.jhelm.app.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.service.ConfigServerValuesLoader;
import org.alexmond.jhelm.core.service.ExternalCommandPostRenderer;
import org.alexmond.jhelm.core.util.RenderedManifest;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.alexmond.jhelm.core.util.ValuesProfiles;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * Implements {@code jhelm template RELEASE CHART}, rendering a chart's templates locally
 * without contacting a cluster. Supports values overrides and post-renderers.
 */
@Component
@CommandLine.Command(name = "template", mixinStandardHelpOptions = true, description = "Locally render templates")
@Slf4j
public class TemplateCommand implements Callable<Integer> {

	private final TemplateAction templateAction;

	private final JhelmCoreProperties coreProperties;

	private final ConfigServerValuesLoader configServerValuesLoader;

	@CommandLine.Mixin
	private final ConfigServerCliOptions configServerOptions = new ConfigServerCliOptions();

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Parameters(index = "1", description = "chart path")
	private String chartPath;

	@Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@Option(names = { "-P", "--profile" }, split = ",",
			description = "active value profile(s): gate spring.config.activate.on-profile documents and select "
					+ "values-<profile>.yaml sidecars (comma-separated or repeatable)")
	private List<String> profileNames = new ArrayList<>();

	@Option(names = { "-f", "--values" }, description = "specify values YAML files")
	private List<String> valuesFiles = new ArrayList<>();

	@Option(names = { "--set" }, description = "set values on the command line (key=value, dot notation supported)")
	private List<String> setValues = new ArrayList<>();

	@Option(names = { "--set-string" }, description = "set STRING values on the command line (no type coercion)")
	private List<String> setStringValues = new ArrayList<>();

	@Option(names = { "--set-file" }, description = "set values from files (key=path), value is the file contents")
	private List<String> setFileValues = new ArrayList<>();

	@Option(names = { "--set-json" }, description = "set JSON values on the command line (key=json)")
	private List<String> setJsonValues = new ArrayList<>();

	@Option(names = { "--set-literal" },
			description = "set a literal STRING value on the command line (key=value, no coercion or escaping)")
	private List<String> setLiteralValues = new ArrayList<>();

	@Option(names = { "--post-renderer" }, description = "path to an executable to use as a post-renderer")
	private List<String> postRenderers = new ArrayList<>();

	@Option(names = { "--kube-version" },
			description = "Kubernetes version used for .Capabilities.KubeVersion (e.g. v1.29.0)")
	private String kubeVersion;

	@Option(names = { "-a", "--api-versions" }, split = ",",
			description = "Kubernetes API versions advertised for .Capabilities.APIVersions.Has "
					+ "(comma-separated or repeatable; additive to the built-in default set)")
	private List<String> apiVersions = new ArrayList<>();

	@Option(names = { "-s", "--show-only" }, description = "only show manifests rendered from the given template(s) "
			+ "(e.g. templates/deployment.yaml; repeatable)")
	private List<String> showOnly = new ArrayList<>();

	@Option(names = { "--output-dir" },
			description = "write the rendered templates into files under this directory instead of stdout")
	private String outputDir;

	@Option(names = { "--skip-tests" }, description = "skip tests from the rendered output (chart test hooks)")
	private boolean skipTests;

	@Option(names = { "--include-crds" }, description = "include CRDs (the chart's crds/ manifests) in the output")
	private boolean includeCrds;

	@Option(names = { "--is-upgrade" },
			description = "set .Release.IsUpgrade instead of .Release.IsInstall when rendering")
	private boolean isUpgrade;

	/**
	 * Creates the command.
	 * @param templateAction the action that renders chart templates
	 * @param coreProperties core config, for the default active value profiles
	 * @param configServerValuesLoader loads chart values from a config server (disabled
	 * by default)
	 */
	public TemplateCommand(TemplateAction templateAction, JhelmCoreProperties coreProperties,
			ConfigServerValuesLoader configServerValuesLoader) {
		this.templateAction = templateAction;
		this.coreProperties = coreProperties;
		this.configServerValuesLoader = configServerValuesLoader;
	}

	@Override
	public Integer call() {
		try {
			ValuesProfiles profiles = ValuesProfiles
				.of(profileNames.isEmpty() ? coreProperties.getProfiles().getActive() : profileNames);
			ConfigServerValuesLoader.Result configServer = configServerValuesLoader.load(name, profiles.active(),
					configServerOptions.toOptions());
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, profiles, configServer.values(),
					configServer.overrideNone(), configServer.overrideSystemProperties(), setValues, setStringValues,
					setFileValues, setJsonValues, setLiteralValues);
			String manifest = templateAction.render(chartPath, name, namespace, overrides, profiles, kubeVersion,
					apiVersions, isUpgrade, includeCrds);
			for (String renderer : postRenderers) {
				manifest = new ExternalCommandPostRenderer(List.of(renderer)).process(manifest);
			}
			if (skipTests) {
				manifest = RenderedManifest.skipTests(manifest);
			}
			if (!showOnly.isEmpty()) {
				manifest = RenderedManifest.showOnly(manifest, showOnly);
			}
			if (outputDir != null) {
				writeToDir(manifest);
			}
			else {
				CliOutput.println(manifest);
			}
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error rendering template: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	// Writes each rendered document into <outputDir>/<chart>/templates/<file>, grouping
	// documents by their source template (as `helm template --output-dir` does), and
	// prints one
	// `wrote <path>` line per file.
	private void writeToDir(String manifest) throws IOException {
		Path base = Path.of(outputDir);
		for (Map.Entry<String, String> entry : RenderedManifest.groupBySource(manifest).entrySet()) {
			String source = entry.getKey();
			Path target = source.isEmpty() ? base.resolve("manifest.yaml") : base.resolve(source);
			Files.createDirectories(target.getParent());
			Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
			CliOutput.println("wrote " + target);
		}
	}

}
