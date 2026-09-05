package com.web.crawler.web_crawler.infrastructure.crawler;

import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsScraperException;
import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Thin HTTP boundary responsible only for fetching the raw Hacker News
 * homepage HTML. Kept separate from {@link HackerNewsScraper} so the HTML
 * parsing logic (and its tests) never depend on a real or mocked HTTP call.
 */
@Component
public class HackerNewsClient {

    private static final Logger log = LoggerFactory.getLogger(HackerNewsClient.class);

    private final WebClient webClient;

    public HackerNewsClient(WebClient hackerNewsWebClient) {
        this.webClient = hackerNewsWebClient;
    }

    public Mono<String> fetchHomepageHtml() {
        log.info("Requesting Hacker News homepage");
        return webClient.get()
                .retrieve()
                .onStatus(status -> status.isError(), response -> Mono.error(
                        new HackerNewsUnavailableException(
                                "Hacker News responded with status " + response.statusCode())))
                .bodyToMono(String.class)
                .doOnNext(html -> log.info("Hacker News homepage fetched successfully"))
                .onErrorMap(ex -> !(ex instanceof HackerNewsScraperException),
                        ex -> new HackerNewsUnavailableException("Unable to reach Hacker News", ex));
    }
}
