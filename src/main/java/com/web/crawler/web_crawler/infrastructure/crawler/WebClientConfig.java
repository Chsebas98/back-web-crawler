package com.web.crawler.web_crawler.infrastructure.crawler;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the WebClient used to fetch the Hacker News homepage.
 *
 * WebClient (not RestTemplate) is required here: the whole stack is WebFlux,
 * and a blocking client would tie up event-loop threads under load. Timeouts
 * are set explicitly so a slow/unresponsive Hacker News fails fast instead of
 * hanging the request indefinitely.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient hackerNewsWebClient(HackerNewsProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.timeout().toMillis())
                .responseTimeout(properties.timeout());

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
