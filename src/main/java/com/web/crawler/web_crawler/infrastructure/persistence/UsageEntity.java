package com.web.crawler.web_crawler.infrastructure.persistence;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC row mapping for {@code crawler_usage} (see
 * db/migration/V1__database.sql). Kept separate from the {@code domain}
 * package and never exposed through the REST API directly: it is a
 * persistence detail, not a domain concept.
 *
 * {@code filter} and {@code status} are stored as plain strings (the enum
 * name) since Spring Data R2DBC has no first-class enum-to-varchar mapping;
 * converting at the service boundary keeps this record a simple 1:1 table
 * mapping.
 */
@Table("crawler_usage")
public record UsageEntity(
        @Id Long id,
        OffsetDateTime requestTimestamp,
        String filter,
        Long executionTimeMs,
        Integer storiesScraped,
        Integer storiesReturned,
        String status,
        String errorMessage) {

    /** Factory for a brand-new row: id is null so R2DBC performs an insert. */
    public static UsageEntity newRecord(
            OffsetDateTime requestTimestamp,
            String filter,
            Long executionTimeMs,
            Integer storiesScraped,
            Integer storiesReturned,
            String status,
            String errorMessage) {
        return new UsageEntity(null, requestTimestamp, filter, executionTimeMs, storiesScraped, storiesReturned, status, errorMessage);
    }
}
