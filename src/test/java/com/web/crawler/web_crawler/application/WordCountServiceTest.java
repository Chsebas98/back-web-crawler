package com.web.crawler.web_crawler.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exercises the exact word-counting semantics from the exercise: only
 * space-separated tokens containing a letter/digit count as words.
 */
class WordCountServiceTest {

    private final WordCountService service = new WordCountService();

    @ParameterizedTest(name = "\"{0}\" -> {1} words")
    @MethodSource("titlesAndExpectedCounts")
    void countsWordsAccordingToTheExerciseRule(String title, int expectedCount) {
        assertThat(service.countWords(title)).isEqualTo(expectedCount);
    }

    private static Stream<Arguments> titlesAndExpectedCounts() {
        return Stream.of(
                Arguments.of("This is a title", 4),
                Arguments.of("This is - a self-explained example", 5),
                Arguments.of("Hello, world!", 2),
                Arguments.of("One  two   three", 3),
                Arguments.of("---", 0),
                Arguments.of("", 0),
                Arguments.of("   ", 0),
                Arguments.of("self-explained title", 2),
                Arguments.of((String) null, 0),
                Arguments.of("  leading and trailing spaces  ", 4));
    }
}
