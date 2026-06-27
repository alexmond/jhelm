package org.alexmond.jhelm.mcp.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class JhelmMcpSampleApplicationTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertNotNull(this.context);
		assertTrue(this.context.containsBean("jhelmChartTools"), "chart MCP tools should be registered");
		assertTrue(this.context.containsBean("jhelmHubTools"), "hub MCP tools should be registered");
	}

}
