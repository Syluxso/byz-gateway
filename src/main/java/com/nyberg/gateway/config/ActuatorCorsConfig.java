package com.nyberg.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Gateway {@code globalcors} applies to proxied routes, not local Actuator endpoints.
 * Admin health probes hit {@code /actuator/health} on api.* — those need this filter.
 */
@Configuration
public class ActuatorCorsConfig {

    @Bean
    public CorsWebFilter actuatorCorsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("X-Request-Id");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/actuator/**", config);
        return new CorsWebFilter(source);
    }
}
