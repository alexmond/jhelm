package org.alexmond.jhelm.app;

import org.springframework.stereotype.Component;
import picocli.CommandLine.IVersionProvider;

/**
 * Supplies {@code jhelm --version} / {@code -V} from {@link VersionInfo} (Spring Boot
 * build-info, the same {@link org.springframework.boot.info.BuildProperties} source
 * Actuator's {@code /actuator/info} uses), rather than a hardcoded literal. Instantiated
 * as a Spring bean so picocli's Spring factory injects the shared {@link VersionInfo}.
 */
@Component
public class JhelmVersionProvider implements IVersionProvider {

	private final VersionInfo versionInfo;

	JhelmVersionProvider(VersionInfo versionInfo) {
		this.versionInfo = versionInfo;
	}

	@Override
	public String[] getVersion() {
		return new String[] { "jhelm " + this.versionInfo.version() };
	}

}
