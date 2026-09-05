package com.web.crawler.web_crawler.infrastructure.crawler.exception;

/**
 * Raised when the Hacker News homepage was fetched successfully but its
 * content could not be trusted: an expected HTML element is missing, or
 * fewer than the expected number of stories were found. Hacker News changing
 * its markup is the most likely real-world cause.
 *
 * Kept distinct from {@link HackerNewsUnavailableException} because this
 * signals a scraper/markup mismatch that needs a code fix, not a transient
 * network issue that a retry could resolve.
 */
public final class HackerNewsParsingException extends HackerNewsScraperException {

    public HackerNewsParsingException(String message) {
        super(message);
    }
}
