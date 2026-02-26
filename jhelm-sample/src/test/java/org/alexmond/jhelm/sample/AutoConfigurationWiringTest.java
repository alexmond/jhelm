package org.alexmond.jhelm.sample;

import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.service.SchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that jhelm-core auto-configuration wires all expected beans into a plain
 * Spring Boot application without any explicit {@code @Import} or {@code @ComponentScan}.
 */
@SpringBootTest
class AutoConfigurationWiringTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private Engine engine;

	@Autowired
	private RepoManager repoManager;

	@Autowired
	private ChartLoader chartLoader;

	@Autowired
	private TemplateAction templateAction;

	@Autowired
	private SchemaValidator schemaValidator;

	@Autowired
	private CreateAction createAction;

	@Autowired
	private ShowAction showAction;

	@Test
	void coreBeansAreAutoWired() {
		assertNotNull(engine, "Engine should be auto-configured");
		assertNotNull(repoManager, "RepoManager should be auto-configured");
		assertNotNull(chartLoader, "ChartLoader should be auto-configured");
		assertNotNull(templateAction, "TemplateAction should be auto-configured");
		assertNotNull(schemaValidator, "SchemaValidator should be auto-configured");
		assertNotNull(createAction, "CreateAction should be auto-configured");
		assertNotNull(showAction, "ShowAction should be auto-configured");
	}

	@Test
	void installActionAbsentWithoutKubeService() {
		assertFalse(context.containsBean("installAction"), "InstallAction should not be present without KubeService");
		assertFalse(context.getBeanNamesForType(InstallAction.class).length > 0,
				"No InstallAction bean should exist without KubeService");
		assertFalse(context.getBeanNamesForType(KubeService.class).length > 0,
				"No KubeService bean should exist without jhelm-kube");
	}

}
