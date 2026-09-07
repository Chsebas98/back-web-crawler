package com.web.crawler.web_crawler.application;

import com.web.crawler.web_crawler.domain.StoryFilter;
import com.web.crawler.web_crawler.domain.UsageStatus;
import com.web.crawler.web_crawler.infrastructure.persistence.UsageEntity;
import com.web.crawler.web_crawler.infrastructure.persistence.UsageRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Records one {@code crawler_usage} row per filtering request.
 *
 * The request timestamp is always generated here, in the backend, never
 * accepted from a caller. Execution time is measured by whoever runs the
 * scrape/filter pipeline and passed in, since this service's only job is
 * persistence, not timing.
 *
 * Design decision: a failure to persist usage data must never fail the API
 * request it is describing. Usage tracking is an audit side-effect, not part
 * of the crawler's core contract with its callers, so persistence errors are
 * logged and swallowed here rather than propagated.
 */
@Service
public class UsageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(UsageTrackingService.class);

    private final UsageRepository repository;

    public UsageTrackingService(UsageRepository repository) {
        this.repository = repository;
    }

    /** Records a request that scraped, filtered and returned stories successfully. */
    public Mono<Void> recordSuccess(StoryFilter filter, Duration executionTime, int storiesScraped, int storiesReturned) {
        return save(UsageEntity.newRecord(
                OffsetDateTime.now(),
                filter.name(),
                executionTime.toMillis(),
                storiesScraped,
                storiesReturned,
                UsageStatus.SUCCESS.name(),
                null));
    }

    /** Records a request that failed (e.g. Hacker News was unreachable or returned malformed data). */
    public Mono<Void> recordFailure(StoryFilter filter, Duration executionTime, String errorMessage) {
        return save(UsageEntity.newRecord(
                OffsetDateTime.now(),
                filter.name(),
                executionTime.toMillis(),
                null,
                null,
                UsageStatus.ERROR.name(),
                errorMessage));
    }

    private Mono<Void> save(UsageEntity entity) {
        return repository.save(entity)
                .doOnNext(saved -> log.info("Usage recorded: filter={}, status={}", saved.filter(), saved.status()))
                .then()
                .onErrorResume(ex -> {
                    log.error("Failed to persist usage record for filter={}; ignoring so the API response is unaffected",
                            entity.filter(), ex);
                    return Mono.empty();
                });
    }
}
