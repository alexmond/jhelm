package org.alexmond.jhelm.rest.dto;

import lombok.Builder;
import lombok.Data;

import org.alexmond.jhelm.core.action.TestAction;

@Data
@Builder
@SuppressWarnings({ "PMD.TestClassWithoutTestCases", "PMD.UnitTestShouldUseTestAnnotation" })
public class TestResultDto {

	private String kind;

	private String name;

	private String status;

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
