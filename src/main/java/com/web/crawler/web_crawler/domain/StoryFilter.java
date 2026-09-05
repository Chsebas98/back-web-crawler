package com.web.crawler.web_crawler.domain;

/**
 * The two filters the API supports. Modeled as an enum (not raw strings) so
 * an invalid value is rejected at the boundary and the valid set is
 * discoverable from the type itself, per the exercise's "no arbitrary
 * strings" requirement. Also stored on {@code crawler_usage.filter} to
 * record which filter a request applied.
 */
public enum StoryFilter {

    /** Titles with more than 5 words, sorted by comments descending. */
    MORE_THAN_FIVE_WORDS,

    /** Titles with 5 or fewer words, sorted by points descending. */
    FIVE_OR_FEWER_WORDS
}
