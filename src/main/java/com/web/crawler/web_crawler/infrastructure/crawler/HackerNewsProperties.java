package com.web.crawler.web_crawler.infrastructure.crawler;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized scraper configuration (see application.properties /
 * HACKERNEWS_BASE_URL, HACKERNEWS_TIMEOUT env vars). Kept out of code so the
 * target site and request timeout can change per environment without a
 * rebuild.
 */
@ConfigurationProperties(prefix = "hackernews")
public record HackerNewsProperties(String baseUrl, Duration timeout) {
}
