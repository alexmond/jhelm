package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@CommandLine.Command(name = "template", description = "locally render templates")
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

	public TemplateCommand(TemplateAction templateAction) {
		this.templateAction = templateAction;
	}

	@Override
	public void run() {
		try {
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, setValues);
			String manifest = templateAction.render(chartPath, name, namespace, overrides);
			log.info("\n{}", manifest);
		}
		catch (Exception ex) {
			log.error("Error rendering template: {}", ex.getMessage());
		}
	}

}
