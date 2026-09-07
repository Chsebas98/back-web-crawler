package com.web.crawler.web_crawler.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Allows the separately-hosted React/Vite frontend to call the API from a
 * different origin. Only the configured origin(s) are allowed (see
 * {@link CorsProperties}) - no wildcard - and only GET, since /api/stories
 * is the only endpoint exposed.
 */
@Configuration
public class CorsConfig implements WebFluxConfigurer {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]))
                .allowedMethods("GET");
    }
}
