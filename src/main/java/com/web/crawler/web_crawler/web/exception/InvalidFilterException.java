package com.web.crawler.web_crawler.web.exception;

/**
 * Raised when the {@code filter} query parameter is missing or does not
 * match a known {@link com.web.crawler.web_crawler.domain.StoryFilter}.
 * There is deliberately no default filter to fall back to: the exercise
 * requires an explicit 4xx instead of silently guessing what the caller meant.
 */
public class InvalidFilterException extends RuntimeException {

    public InvalidFilterException(String message) {
        super(message);
    }
}
