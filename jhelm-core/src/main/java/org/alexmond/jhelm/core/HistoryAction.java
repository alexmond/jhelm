package org.alexmond.jhelm.core;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HistoryAction {

	private final KubeService kubeService;

	public List<Release> history(String name, String namespace) throws Exception {
		return kubeService.getReleaseHistory(name, namespace);
	}

}
