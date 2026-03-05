package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import lombok.Data;

@Data
public class UpgradeRequest {

	private String chartPath;

	private Map<String, Object> values;

	private boolean dryRun;

}
