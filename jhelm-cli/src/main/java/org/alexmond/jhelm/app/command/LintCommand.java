package org.alexmond.jhelm.app.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Component
@CommandLine.Command(name = "lint", mixinStandardHelpOptions = true,
		description = "Examine a chart for possible issues")
public class LintCommand implements Runnable {

	private final LintAction lintAction;

	@CommandLine.Parameters(index = "0", defaultValue = ".", description = "chart path")
	private String chartPath;

	@Option(names = { "-f", "--values" }, description = "specify values YAML files")
	private List<String> valuesFiles = new ArrayList<>();

	@Option(names = { "--set" }, description = "set values on the command line (key=value)")
	private List<String> setValues = new ArrayList<>();

	@Option(names = { "--strict" }, defaultValue = "false", description = "fail on lint warnings")
	private boolean strict;

	public LintCommand(LintAction lintAction) {
		this.lintAction = lintAction;
	}

	@Override
	public void run() {
		try {
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, setValues);
			LintAction.LintResult result = lintAction.lint(chartPath, overrides, strict);

			CliOutput.println("==> Linting " + result.getChartPath());

			for (String warning : result.getWarnings()) {
				CliOutput.println("[WARNING] " + warning);
			}
			for (String error : result.getErrors()) {
				CliOutput.errPrintln("[ERROR] " + error);
			}

			if (result.isOk() && (!strict || result.getWarnings().isEmpty())) {
				CliOutput.println("1 chart(s) linted, 0 chart(s) failed");
			}
			else {
				CliOutput.errPrintln(CliOutput.error("1 chart(s) linted, 1 chart(s) failed"));
			}
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error: " + ex.getMessage()));
		}
	}

}
