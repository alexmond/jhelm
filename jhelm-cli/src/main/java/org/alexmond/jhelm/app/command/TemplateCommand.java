package org.alexmond.jhelm.app.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.service.ExternalCommandPostRenderer;
import org.alexmond.jhelm.core.util.ValuesOverrides;
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
public class TemplateCommand implements Runnable {

	private final TemplateAction templateAction;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Parameters(index = "1", description = "chart path")
	private String chartPath;

	@Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

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

	@Option(names = { "--post-renderer" }, description = "path to an executable to use as a post-renderer")
	private List<String> postRenderers = new ArrayList<>();

	/**
	 * Creates the command.
	 * @param templateAction the action that renders chart templates
	 */
	public TemplateCommand(TemplateAction templateAction) {
		this.templateAction = templateAction;
	}

	@Override
	public void run() {
		try {
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, setValues, setStringValues,
					setFileValues, setJsonValues);
			String manifest = templateAction.render(chartPath, name, namespace, overrides);
			for (String renderer : postRenderers) {
				manifest = new ExternalCommandPostRenderer(List.of(renderer)).process(manifest);
			}
			CliOutput.println(manifest);
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error rendering template: " + ex.getMessage()));
		}
	}

}
