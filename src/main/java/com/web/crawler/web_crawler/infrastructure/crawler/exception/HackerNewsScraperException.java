package com.web.crawler.web_crawler.infrastructure.crawler.exception;

/**
 * Base type for every failure the Hacker News scraper can raise.
 *
 * Kept unchecked (extends RuntimeException) because these errors propagate
 * through a reactive pipeline (Mono/Flux), where checked exceptions do not
 * fit the functional error-signaling model.
 */
public sealed class HackerNewsScraperException extends RuntimeException
        permits HackerNewsUnavailableException, HackerNewsParsingException {

    public HackerNewsScraperException(String message) {
        super(message);
    }

    public HackerNewsScraperException(String message, Throwable cause) {
        super(message, cause);
    }
}
