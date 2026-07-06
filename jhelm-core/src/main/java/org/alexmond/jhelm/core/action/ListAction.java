package org.alexmond.jhelm.core.action;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;

@RequiredArgsConstructor
public class ListAction {

	private final KubeService kubeService;

	public List<Release> list(String namespace) {
		return kubeService.listReleases(namespace);
	}

	/**
	 * Lists the latest revision of every release across all namespaces (Helm
	 * {@code list --all-namespaces}).
	 * @return the releases found across all namespaces
	 */
	public List<Release> listAll() {
		return kubeService.listAllReleases();
	}

}
