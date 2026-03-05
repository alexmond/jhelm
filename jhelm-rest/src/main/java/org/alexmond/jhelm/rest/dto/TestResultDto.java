package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.action.TestAction;

@Data
@Builder
@Schema(description = "Result of a Helm test execution")
@SuppressWarnings({ "PMD.TestClassWithoutTestCases", "PMD.UnitTestShouldUseTestAnnotation" })
public class TestResultDto {

	@Schema(description = "Kubernetes resource kind", example = "Pod")
	private String kind;

	@Schema(description = "Test resource name", example = "my-release-test")
	private String name;

	@Schema(description = "Test result status", example = "PASSED")
	private String status;

	@Schema(description = "Test output message")
	private String message;

	public static TestResultDto from(TestAction.TestResult result) {
		return TestResultDto.builder()
			.kind(result.getKind())
			.name(result.getName())
			.status(result.getStatus().name())
			.message(result.getMessage())
			.build();
	}

}
