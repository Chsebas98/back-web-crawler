package com.web.crawler.web_crawler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.crawler.web_crawler.domain.StoryFilter;
import com.web.crawler.web_crawler.domain.UsageStatus;
import com.web.crawler.web_crawler.infrastructure.persistence.UsageEntity;
import com.web.crawler.web_crawler.infrastructure.persistence.UsageRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Uses a mocked {@link UsageRepository} so no real PostgreSQL instance is
 * required, per the exercise's testing guidance.
 */
class UsageTrackingServiceTest {

    private final UsageRepository repository = mock(UsageRepository.class);
    private final UsageTrackingService service = new UsageTrackingService(repository);

    @Test
    void recordsASuccessfulRequestWithABackendGeneratedTimestamp() {
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.recordSuccess(StoryFilter.MORE_THAN_FIVE_WORDS, Duration.ofMillis(250), 30, 12))
                .verifyComplete();

        ArgumentCaptor<UsageEntity> captor = ArgumentCaptor.forClass(UsageEntity.class);
        verify(repository).save(captor.capture());
        UsageEntity saved = captor.getValue();

        assertThat(saved.id()).isNull();
        assertThat(saved.requestTimestamp()).isNotNull();
        assertThat(saved.filter()).isEqualTo(StoryFilter.MORE_THAN_FIVE_WORDS.name());
        assertThat(saved.executionTimeMs()).isEqualTo(250L);
        assertThat(saved.storiesScraped()).isEqualTo(30);
        assertThat(saved.storiesReturned()).isEqualTo(12);
        assertThat(saved.status()).isEqualTo(UsageStatus.SUCCESS.name());
        assertThat(saved.errorMessage()).isNull();
    }

    @Test
    void recordsAFailedRequestWithTheErrorMessageAndNoStoryCounts() {
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.recordFailure(
                        StoryFilter.FIVE_OR_FEWER_WORDS, Duration.ofMillis(80), "Hacker News unavailable"))
                .verifyComplete();

        ArgumentCaptor<UsageEntity> captor = ArgumentCaptor.forClass(UsageEntity.class);
        verify(repository).save(captor.capture());
        UsageEntity saved = captor.getValue();

        assertThat(saved.filter()).isEqualTo(StoryFilter.FIVE_OR_FEWER_WORDS.name());
        assertThat(saved.status()).isEqualTo(UsageStatus.ERROR.name());
        assertThat(saved.errorMessage()).isEqualTo("Hacker News unavailable");
        assertThat(saved.storiesScraped()).isNull();
        assertThat(saved.storiesReturned()).isNull();
    }

    @Test
    void aPersistenceFailureIsSwallowedSoItDoesNotAffectTheCaller() {
        when(repository.save(any())).thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(service.recordSuccess(StoryFilter.MORE_THAN_FIVE_WORDS, Duration.ofMillis(10), 30, 5))
                .verifyComplete();
    }
}
