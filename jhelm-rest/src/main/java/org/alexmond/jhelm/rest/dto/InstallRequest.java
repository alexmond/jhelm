package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import lombok.Data;

@Data
public class InstallRequest {

	private String chartPath;

	private String releaseName;

	private String namespace = "default";

	private Map<String, Object> values;

	private boolean dryRun;

}
