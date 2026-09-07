package com.web.crawler.web_crawler.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.web.crawler.web_crawler.application.StoryService;
import com.web.crawler.web_crawler.domain.Story;
import com.web.crawler.web_crawler.domain.StoryFilter;
import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsUnavailableException;
import com.web.crawler.web_crawler.web.exception.GlobalErrorWebExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * End-to-end test of the web layer only: {@link StoryService} is mocked, so
 * this verifies request handling (filter validation, status codes, the
 * {@code ApiResponse} envelope, error mapping) without touching the scraper,
 * filtering logic, or a database.
 *
 * {@link GlobalErrorWebExceptionHandler} is explicitly imported since it is
 * the sole source of error responses in this app (there is no
 * {@code @RestControllerAdvice}) and a WebFlux test slice does not wire it up
 * automatically.
 */
@WebFluxTest(controllers = StoryController.class)
@Import({SecurityConfig.class, GlobalErrorWebExceptionHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
class StoryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private StoryService storyService;

    @Test
    void returnsAnEnvelopeWithTheStoriesForAValidFilter() {
        List<Story> stories = List.of(new Story(1, "This Title Has More Than Five Words", 10, 50));
        when(storyService.getStories(StoryFilter.MORE_THAN_FIVE_WORDS)).thenReturn(Mono.just(stories));

        webTestClient.get().uri("/api/stories?filter=MORE_THAN_FIVE_WORDS")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.response").isEqualTo(true)
                .jsonPath("$.statusCode").isEqualTo(200)
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.errorDetail").doesNotExist()
                .jsonPath("$.result[0].number").isEqualTo(1)
                .jsonPath("$.result[0].title").isEqualTo("This Title Has More Than Five Words")
                .jsonPath("$.result[0].points").isEqualTo(10)
                .jsonPath("$.result[0].comments").isEqualTo(50);
    }

    @Test
    void rejectsAnUnknownFilterWithAFailureEnvelope() {
        webTestClient.get().uri("/api/stories?filter=NOT_A_REAL_FILTER")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.response").isEqualTo(false)
                .jsonPath("$.statusCode").isEqualTo(400)
                .jsonPath("$.result").doesNotExist()
                .jsonPath("$.errorDetail").isNotEmpty();
    }

    @Test
    void rejectsAMissingFilterParameterWithAFailureEnvelope() {
        webTestClient.get().uri("/api/stories")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.response").isEqualTo(false)
                .jsonPath("$.statusCode").isEqualTo(400);
    }

    @Test
    void mapsAScrapingFailureToABadGatewayFailureEnvelope() {
        when(storyService.getStories(eq(StoryFilter.FIVE_OR_FEWER_WORDS)))
                .thenReturn(Mono.error(new HackerNewsUnavailableException("Hacker News is down")));

        webTestClient.get().uri("/api/stories?filter=FIVE_OR_FEWER_WORDS")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.response").isEqualTo(false)
                .jsonPath("$.statusCode").isEqualTo(502)
                .jsonPath("$.result").doesNotExist()
                // the original exception message must never reach the caller
                .jsonPath("$.errorDetail").value(detail -> {
                    String text = detail.toString();
                    if (text.contains("Hacker News")) {
                        throw new AssertionError("errorDetail leaked an internal message: " + text);
                    }
                });
    }

    @Test
    void returnsAFailureEnvelopeForAnUnmappedRoute() {
        webTestClient.get().uri("/api/stories/does-not-exist")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.response").isEqualTo(false)
                .jsonPath("$.statusCode").isEqualTo(404)
                .jsonPath("$.result").doesNotExist();
    }
}
