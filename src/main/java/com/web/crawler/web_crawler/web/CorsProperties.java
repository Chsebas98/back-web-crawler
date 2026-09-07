package com.web.crawler.web_crawler.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized CORS configuration (see application.properties /
 * CORS_ALLOWED_ORIGINS). Kept out of code, and never wildcarded, so each
 * environment (local dev, staging, prod) can point at its own frontend
 * origin without a rebuild.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
}
