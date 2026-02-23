package org.alexmond.jhelm.core;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ListAction {

	private final KubeService kubeService;

	public List<Release> list(String namespace) throws Exception {
		return kubeService.listReleases(namespace);
	}

}
