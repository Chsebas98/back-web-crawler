package com.web.crawler.web_crawler.infrastructure.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.web.crawler.web_crawler.domain.Story;
import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsParsingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Parsing tests use static HTML fixtures under src/test/resources/fixtures
 * instead of the live Hacker News site, so they are deterministic and do not
 * depend on network access. {@link HackerNewsClient} is mocked out entirely:
 * these tests only exercise the HTML-to-{@link Story} conversion.
 */
class HackerNewsScraperTest {

    private final HackerNewsClient client = mock(HackerNewsClient.class);
    private final HackerNewsScraper scraper = new HackerNewsScraper(client);

    @Test
    void parsesTheFirstThirtyStoriesFromTheHomepage() {
        when(client.fetchHomepageHtml()).thenReturn(Mono.just(fixture("hackernews-homepage.html")));

        StepVerifier.create(scraper.scrapeTopStories())
                .assertNext(stories -> {
                    assertThat(stories).hasSize(30);

                    Story first = stories.get(0);
                    assertThat(first.number()).isEqualTo(1);
                    assertThat(first.title()).isEqualTo("Example Story Title Number 1 With Enough Words To Test");
                    assertThat(first.points()).isEqualTo(101);
                    assertThat(first.comments()).isEqualTo(11);

                    Story last = stories.get(29);
                    assertThat(last.number()).isEqualTo(30);
                })
                .verifyComplete();
    }

    @Test
    void mapsTheDiscussLinkToZeroComments() {
        when(client.fetchHomepageHtml()).thenReturn(Mono.just(fixture("hackernews-homepage.html")));

        StepVerifier.create(scraper.scrapeTopStories())
                .assertNext(stories -> {
                    Story storyWithNoComments = stories.get(14); // rank 15 in the fixture
                    assertThat(storyWithNoComments.number()).isEqualTo(15);
                    assertThat(storyWithNoComments.comments()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void defaultsPointsAndCommentsToZeroWhenAbsentLikeAJobPosting() {
        when(client.fetchHomepageHtml()).thenReturn(Mono.just(fixture("hackernews-homepage.html")));

        StepVerifier.create(scraper.scrapeTopStories())
                .assertNext(stories -> {
                    Story jobLikeEntry = stories.get(19); // rank 20 in the fixture
                    assertThat(jobLikeEntry.number()).isEqualTo(20);
                    assertThat(jobLikeEntry.points()).isZero();
                    assertThat(jobLikeEntry.comments()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void failsWhenFewerThanThirtyStoriesAreFound() {
        when(client.fetchHomepageHtml())
                .thenReturn(Mono.just(fixture("hackernews-homepage-insufficient.html")));

        StepVerifier.create(scraper.scrapeTopStories())
                .expectError(HackerNewsParsingException.class)
                .verify();
    }

    @Test
    void failsWhenATitleElementIsMissing() {
        when(client.fetchHomepageHtml())
                .thenReturn(Mono.just(fixture("hackernews-homepage-missing-title.html")));

        StepVerifier.create(scraper.scrapeTopStories())
                .expectError(HackerNewsParsingException.class)
                .verify();
    }

    @Test
    void propagatesClientErrorsWithoutSwallowingThem() {
        RuntimeException clientFailure = new RuntimeException("boom");
        when(client.fetchHomepageHtml()).thenReturn(Mono.error(clientFailure));

        StepVerifier.create(scraper.scrapeTopStories())
                .expectErrorMatches(clientFailure::equals)
                .verify();
    }

    private String fixture(String fileName) {
        try {
            Path path = Path.of("src/test/resources/fixtures", fileName);
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
