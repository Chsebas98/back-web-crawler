package com.web.crawler.web_crawler.domain;

/**
 * A single Hacker News front-page entry.
 *
 * Immutable by design: a Story is a snapshot of a scraped entry and is never
 * mutated after creation. Modeled as a record since it is a plain data holder
 * with no behavior of its own.
 */
public record Story(Integer number, String title, Integer points, Integer comments) {
}
