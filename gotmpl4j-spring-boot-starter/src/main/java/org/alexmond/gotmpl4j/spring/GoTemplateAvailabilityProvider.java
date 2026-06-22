package org.alexmond.gotmpl4j.spring;

import org.springframework.boot.autoconfigure.template.TemplateAvailabilityProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

/**
 * {@link TemplateAvailabilityProvider} that reports whether a gotmpl4j view template
 * exists, mirroring Spring Boot's {@code MustacheTemplateAvailabilityProvider}. Spring
 * Boot uses this to decide whether a view name is backed by a template (e.g. for error
 * views).
 */
public class GoTemplateAvailabilityProvider implements TemplateAvailabilityProvider {

	@Override
	public boolean isTemplateAvailable(String view, Environment environment, ClassLoader classLoader,
			ResourceLoader resourceLoader) {
		if (ClassUtils.isPresent("org.alexmond.gotmpl4j.GoTemplate", classLoader)) {
			// Mirror Gotmpl4jProperties defaults; honour the deprecated
			// template-location.
			String prefix = environment.getProperty("gotmpl4j.prefix");
			if (prefix == null) {
				prefix = environment.getProperty("gotmpl4j.template-location", "classpath:/templates/");
			}
			String suffix = environment.getProperty("gotmpl4j.suffix", ".gotmpl");
			return resourceLoader.getResource(prefix + view + suffix).exists();
		}
		return false;
	}

}
