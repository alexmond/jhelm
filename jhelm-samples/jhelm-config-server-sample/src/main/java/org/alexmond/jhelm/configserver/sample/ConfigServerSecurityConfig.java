package org.alexmond.jhelm.configserver.sample;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the config server with stateless HTTP basic auth (the default user from
 * {@code spring.security.user.*}). CSRF is disabled so the {@code /encrypt} and
 * {@code /decrypt} POST endpoints work with a plain {@code curl}, which is how a config
 * server is normally driven.
 */
@Configuration
public class ConfigServerSecurityConfig {

	/**
	 * @param http the security builder
	 * @return a filter chain requiring authentication on every request, basic auth, CSRF
	 * off
	 * @throws Exception if the chain cannot be built
	 */
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
			.httpBasic(Customizer.withDefaults());
		return http.build();
	}

}
