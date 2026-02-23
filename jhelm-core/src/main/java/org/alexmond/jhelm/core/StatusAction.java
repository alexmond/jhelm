package org.alexmond.jhelm.core;

import java.util.Optional;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StatusAction {

	private final KubeService kubeService;

	public Optional<Release> status(String name, String namespace) throws Exception {
		return kubeService.getRelease(name, namespace);
	}

}
