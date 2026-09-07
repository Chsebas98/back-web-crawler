package com.web.crawler.web_crawler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.crawler.web_crawler.domain.Story;
import com.web.crawler.web_crawler.domain.StoryFilter;
import com.web.crawler.web_crawler.infrastructure.crawler.HackerNewsScraper;
import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsUnavailableException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies the scrape -> filter -> track pipeline, with the scraper and
 * usage tracking mocked out so this test is a pure unit test of the
 * orchestration logic.
 */
class StoryServiceTest {

    private final HackerNewsScraper scraper = mock(HackerNewsScraper.class);
    private final StoryFilterService filterService = new StoryFilterService(new WordCountService());
    private final UsageTrackingService usageTrackingService = mock(UsageTrackingService.class);
    private final StoryService service = new StoryService(scraper, filterService, usageTrackingService);

    private static final List<Story> SCRAPED = List.of(
            new Story(1, "This Title Has More Than Five Words", 10, 50),
            new Story(2, "Short Title", 100, 5));

    @Test
    void filtersScrapedStoriesAndRecordsSuccessfulUsage() {
        when(scraper.scrapeTopStories()).thenReturn(Mono.just(SCRAPED));
        when(usageTrackingService.recordSuccess(any(), any(), anyInt(), anyInt())).thenReturn(Mono.empty());

        StepVerifier.create(service.getStories(StoryFilter.MORE_THAN_FIVE_WORDS))
                .assertNext(result -> assertThat(result).extracting(Story::number).containsExactly(1))
                .verifyComplete();

        verify(usageTrackingService).recordSuccess(eq(StoryFilter.MORE_THAN_FIVE_WORDS), any(Duration.class), eq(2), eq(1));
        verify(usageTrackingService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void recordsFailureAndStillPropagatesTheErrorWhenScrapingFails() {
        HackerNewsUnavailableException failure = new HackerNewsUnavailableException("Hacker News is down");
        when(scraper.scrapeTopStories()).thenReturn(Mono.error(failure));
        when(usageTrackingService.recordFailure(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.getStories(StoryFilter.FIVE_OR_FEWER_WORDS))
                .expectErrorMatches(failure::equals)
                .verify();

        verify(usageTrackingService).recordFailure(
                eq(StoryFilter.FIVE_OR_FEWER_WORDS), any(Duration.class), eq("Hacker News is down"));
        verify(usageTrackingService, never()).recordSuccess(any(), any(), anyInt(), anyInt());
    }
}
