package org.alexmond.jhelm.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {CoreConfig.class, MockKubeConfig.class})
class CoreConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void testRepoManagerBean() {
        RepoManager repoManager = context.getBean(RepoManager.class);
        assertNotNull(repoManager);
    }

    @Test
    void testRegistryManagerBean() {
        RegistryManager registryManager = context.getBean(RegistryManager.class);
        assertNotNull(registryManager);
    }

    @Test
    void testEngineBean() {
        Engine engine = context.getBean(Engine.class);
        assertNotNull(engine);
    }

    @Test
    void testInstallActionBean() {
        InstallAction installAction = context.getBean(InstallAction.class);
        assertNotNull(installAction);
    }

    @Test
    void testTemplateActionBean() {
        TemplateAction templateAction = context.getBean(TemplateAction.class);
        assertNotNull(templateAction);
    }

    @Test
    void testChartLoaderBean() {
        ChartLoader chartLoader = context.getBean(ChartLoader.class);
        assertNotNull(chartLoader);
    }
}
