package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Result of chart packaging")
public class PackageResultDto {

	@Schema(description = "Absolute path to the created archive", example = "/tmp/output/nginx-1.0.0.tgz")
	private String archivePath;

}
