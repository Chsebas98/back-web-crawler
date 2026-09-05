package com.web.crawler.web_crawler.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.web.crawler.web_crawler.domain.Story;
import com.web.crawler.web_crawler.domain.StoryFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Deterministic, hand-picked stories so filtering, the exact-5-word
 * boundary, sorting, and tie behavior can all be asserted precisely without
 * depending on the scraper or live data.
 */
class StoryFilterServiceTest {

    // Word counts: 3, 3, 5 (boundary), 7, 8, 6
    private static final Story THREE_WORDS_LOW_POINTS = new Story(1, "Short Title Here", 50, 5);
    private static final Story THREE_WORDS_MID_POINTS = new Story(2, "Another Short One", 80, 5);
    private static final Story EXACTLY_FIVE_WORDS = new Story(3, "Exactly Five Words Right Here", 200, 1);
    private static final Story SEVEN_WORDS_HIGH_COMMENTS = new Story(4, "This Title Has More Than Five Words", 10, 100);
    private static final Story EIGHT_WORDS_TIED_COMMENTS = new Story(5, "Another Long Title With More Than Five Words", 999, 100);
    private static final Story SIX_WORDS_LOW_COMMENTS = new Story(6, "Six Word Title For This One", 5, 50);

    private static final List<Story> STORIES = List.of(
            THREE_WORDS_LOW_POINTS,
            THREE_WORDS_MID_POINTS,
            EXACTLY_FIVE_WORDS,
            SEVEN_WORDS_HIGH_COMMENTS,
            EIGHT_WORDS_TIED_COMMENTS,
            SIX_WORDS_LOW_COMMENTS);

    private final StoryFilterService service = new StoryFilterService(new WordCountService());

    @Test
    void moreThanFiveWordsExcludesTitlesWithExactlyFiveWords() {
        List<Story> result = service.apply(STORIES, StoryFilter.MORE_THAN_FIVE_WORDS);

        assertThat(result).extracting(Story::number).doesNotContain(3);
    }

    @Test
    void moreThanFiveWordsKeepsOnlyTitlesWithMoreThanFiveWordsSortedByCommentsDescending() {
        List<Story> result = service.apply(STORIES, StoryFilter.MORE_THAN_FIVE_WORDS);

        // 4 and 5 tie on comments (100); the stable sort keeps their original
        // relative order, so 4 (which appeared first) stays ahead of 5.
        assertThat(result).extracting(Story::number).containsExactly(4, 5, 6);
    }

    @Test
    void fiveOrFewerWordsIncludesTitlesWithExactlyFiveWords() {
        List<Story> result = service.apply(STORIES, StoryFilter.FIVE_OR_FEWER_WORDS);

        assertThat(result).extracting(Story::number).contains(3);
    }

    @Test
    void fiveOrFewerWordsKeepsOnlyTitlesWithFiveOrFewerWordsSortedByPointsDescending() {
        List<Story> result = service.apply(STORIES, StoryFilter.FIVE_OR_FEWER_WORDS);

        assertThat(result).extracting(Story::number).containsExactly(3, 2, 1);
    }

    @Test
    void bothFiltersTogetherAccountForEveryStoryExactlyOnce() {
        List<Story> moreThanFive = service.apply(STORIES, StoryFilter.MORE_THAN_FIVE_WORDS);
        List<Story> fiveOrFewer = service.apply(STORIES, StoryFilter.FIVE_OR_FEWER_WORDS);

        assertThat(moreThanFive.size() + fiveOrFewer.size()).isEqualTo(STORIES.size());
    }
}
