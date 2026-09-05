package com.web.crawler.web_crawler.infrastructure.crawler.exception;

/**
 * Raised when Hacker News cannot be reached at all: connection failures,
 * timeouts, or a non-2xx HTTP response. These are transport-level failures
 * that a retry might resolve, as opposed to {@link HackerNewsParsingException},
 * which signals that the page was fetched but its content could not be trusted.
 */
public final class HackerNewsUnavailableException extends HackerNewsScraperException {

    public HackerNewsUnavailableException(String message) {
        super(message);
    }

    public HackerNewsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
