package org.alexmond.jhelm.core.action;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;

@RequiredArgsConstructor
public class HistoryAction {

	private final KubeService kubeService;

	public List<Release> history(String name, String namespace) throws Exception {
		return kubeService.getReleaseHistory(name, namespace);
	}

}
