package org.alexmond.jhelm.core.action;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.KubeService;

@RequiredArgsConstructor
public class StatusAction {

	private final KubeService kubeService;

	public Optional<Release> status(String name, String namespace) throws Exception {
		return kubeService.getRelease(name, namespace);
	}

	public List<ResourceStatus> getResourceStatuses(Release release) throws Exception {
		if (release.getManifest() == null || release.getManifest().isBlank()) {
			return List.of();
		}
		return kubeService.getResourceStatuses(release.getNamespace(), release.getManifest());
	}

}
