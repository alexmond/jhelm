package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to verify a chart's PGP signature")
public class VerifyRequest {

	@Schema(description = "Path to the chart .tgz archive", example = "/tmp/nginx-1.0.0.tgz",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartTgzPath;

	@Schema(description = "Path to the PGP public keyring", example = "/tmp/keyring.gpg",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String keyringPath;

}
