package com.web.crawler.web_crawler.application;

import com.web.crawler.web_crawler.domain.Story;
import com.web.crawler.web_crawler.domain.StoryFilter;
import com.web.crawler.web_crawler.infrastructure.crawler.HackerNewsScraper;
import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsScraperException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Orchestrates a single filtering request: scrape -&gt; filter/sort -&gt; record
 * usage. This is the one place that wires the crawler, the filtering logic
 * and usage tracking together, so the controller stays free of business
 * logic.
 *
 * Execution time is measured here (System.nanoTime at the start/end of the
 * pipeline) rather than trusting any client-supplied value, per the
 * exercise's requirement that timing be backend-generated.
 */
@Service
public class StoryService {

    private static final Logger log = LoggerFactory.getLogger(StoryService.class);

    private final HackerNewsScraper scraper;
    private final StoryFilterService filterService;
    private final UsageTrackingService usageTrackingService;

    public StoryService(HackerNewsScraper scraper, StoryFilterService filterService, UsageTrackingService usageTrackingService) {
        this.scraper = scraper;
        this.filterService = filterService;
        this.usageTrackingService = usageTrackingService;
    }

    public Mono<List<Story>> getStories(StoryFilter filter) {
        log.info("Handling stories request with filter={}", filter);
        long startNanos = System.nanoTime();

        return scraper.scrapeTopStories()
                .map(scraped -> {
                    List<Story> filtered = filterService.apply(scraped, filter);
                    return new ScrapeResult(scraped, filtered);
                })
                .flatMap(result -> usageTrackingService
                        .recordSuccess(filter, elapsedSince(startNanos), result.scraped().size(), result.filtered().size())
                        .thenReturn(result.filtered()))
                .onErrorResume(HackerNewsScraperException.class, ex -> recordFailureThenPropagate(filter, startNanos, ex));
    }

    /**
     * Scraping failures are still recorded (status=ERROR) for the audit
     * trail, then re-thrown so the caller still receives a proper error
     * response via the global exception handler. Usage tracking never
     * swallows the original failure - only its own persistence failures are
     * swallowed (see UsageTrackingService).
     */
    private Mono<List<Story>> recordFailureThenPropagate(StoryFilter filter, long startNanos, HackerNewsScraperException ex) {
        return usageTrackingService.recordFailure(filter, elapsedSince(startNanos), ex.getMessage())
                .then(Mono.error(ex));
    }

    private Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private record ScrapeResult(List<Story> scraped, List<Story> filtered) {
    }
}
