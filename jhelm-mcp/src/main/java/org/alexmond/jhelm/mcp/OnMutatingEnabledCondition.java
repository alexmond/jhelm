package org.alexmond.jhelm.mcp;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Spring {@link Condition} that matches only when cluster-mutating operations are enabled
 * under the unified deny-by-default security policy.
 *
 * <p>
 * It reads {@code jhelm.security.mode} and {@code jhelm.security.api-key} directly from
 * the {@link Environment} (a {@code Condition} runs before bean instantiation, so the
 * resolved {@code JhelmSecurityPolicy} bean is not yet available) and matches only when
 * the mode resolves to {@link JhelmAccessMode#FULL FULL} <em>and</em> a non-blank API key
 * is set. Without an API key the mutating MCP tools are never registered.
 */
public class OnMutatingEnabledCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Environment env = context.getEnvironment();
		String mode = env.getProperty("jhelm.security.mode");
		String apiKey = env.getProperty("jhelm.security.api-key");
		boolean full = mode != null && JhelmAccessMode.FULL.name().equalsIgnoreCase(mode.trim());
		return full && StringUtils.hasText(apiKey);
	}

}
