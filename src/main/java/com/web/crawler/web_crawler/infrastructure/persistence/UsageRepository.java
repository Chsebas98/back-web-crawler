package com.web.crawler.web_crawler.infrastructure.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Reactive, non-blocking access to {@code crawler_usage}. Deliberately plain:
 * only the base CRUD operations are needed to record usage.
 */
public interface UsageRepository extends ReactiveCrudRepository<UsageEntity, Long> {
}
