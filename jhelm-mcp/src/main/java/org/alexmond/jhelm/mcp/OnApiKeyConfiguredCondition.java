package org.alexmond.jhelm.mcp;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Spring {@link Condition} that matches only when a non-blank
 * {@code jhelm.security.api-key} is configured.
 *
 * <p>
 * Used to register the {@link McpApiKeyFilter} only when an API key exists. A read-only,
 * no-key MCP server therefore stays open ("read-only is safe to expose"), while
 * configuring a key enforces it on the MCP HTTP endpoint.
 */
public class OnApiKeyConfiguredCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return StringUtils.hasText(context.getEnvironment().getProperty("jhelm.security.api-key"));
	}

}
