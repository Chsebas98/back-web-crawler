package com.web.crawler.web_crawler.infrastructure.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies the HTTP boundary maps failures to {@link HackerNewsUnavailableException}
 * without ever making a real network call: the underlying ExchangeFunction is
 * stubbed directly.
 */
class HackerNewsClientTest {

    @Test
    void returnsTheResponseBodyOnSuccess() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://news.ycombinator.com")
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "text/html")
                                .body("<html>ok</html>")
                                .build()))
                .build();

        StepVerifier.create(new HackerNewsClient(webClient).fetchHomepageHtml())
                .expectNext("<html>ok</html>")
                .verifyComplete();
    }

    @Test
    void mapsANonSuccessStatusToHackerNewsUnavailableException() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://news.ycombinator.com")
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()))
                .build();

        StepVerifier.create(new HackerNewsClient(webClient).fetchHomepageHtml())
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(HackerNewsUnavailableException.class)
                        .hasMessageContaining("503"))
                .verify();
    }

    @Test
    void mapsAConnectionFailureToHackerNewsUnavailableException() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://news.ycombinator.com")
                .exchangeFunction(request -> Mono.error(new RuntimeException("connection refused")))
                .build();

        StepVerifier.create(new HackerNewsClient(webClient).fetchHomepageHtml())
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(HackerNewsUnavailableException.class)
                        .hasCauseInstanceOf(RuntimeException.class))
                .verify();
    }
}
