package org.alexmond.jhelm.core.action;

import org.alexmond.jhelm.core.exception.KubernetesOperationException;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;

/**
 * Implements {@code helm history}: lists the stored revisions of a release in
 * chronological order.
 */
@RequiredArgsConstructor
public class HistoryAction {

	private final KubeService kubeService;

	/**
	 * Returns the revision history of a named release.
	 * @param name the release name
	 * @param namespace the release namespace
	 * @return the list of stored revisions, oldest first
	 * @throws KubernetesOperationException if the release history cannot be read
	 */
	public List<Release> history(String name, String namespace) {
		return kubeService.getReleaseHistory(name, namespace);
	}

}
