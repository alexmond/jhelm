package org.alexmond.jhelm.rest.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.action.LintAction;

@Data
@Builder
@Schema(description = "Result of chart linting")
public class LintResultDto {

	@Schema(description = "Path to the linted chart", example = "/tmp/nginx")
	private String chartPath;

	@Schema(description = "Whether the chart passed linting without errors")
	private boolean ok;

	@Schema(description = "List of lint errors")
	private List<String> errors;

	@Schema(description = "List of lint warnings")
	private List<String> warnings;

	public static LintResultDto from(LintAction.LintResult result) {
		return LintResultDto.builder()
			.chartPath(result.getChartPath())
			.ok(result.isOk())
			.errors(result.getErrors())
			.warnings(result.getWarnings())
			.build();
	}

}
