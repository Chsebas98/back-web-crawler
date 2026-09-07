package com.web.crawler.web_crawler.application;

import com.web.crawler.web_crawler.domain.Story;
import com.web.crawler.web_crawler.domain.StoryFilter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Applies one of the two exercise-defined filters to an already-scraped list
 * of stories, then sorts the result.
 *
 * Filtering/sorting is done in plain memory (Stream, Comparator) rather than
 * with a database query: only 30 stories are ever involved, so an in-memory
 * pass is both simpler and cheaper than round-tripping through a database
 * (which, per the design, only stores usage/audit data, not stories).
 *
 * {@link Comparator#reversed()} on a stable {@code Stream.sorted} preserves
 * the original scrape order for ties, e.g. two stories with equal comments
 * under MORE_THAN_FIVE_WORDS keep their relative rank order.
 */
@Service
public class StoryFilterService {

    /** Boundary between the two filters, per the exercise's word-count rule. */
    static final int WORD_COUNT_THRESHOLD = 5;

    private final WordCountService wordCountService;

    public StoryFilterService(WordCountService wordCountService) {
        this.wordCountService = wordCountService;
    }

    public List<Story> apply(List<Story> stories, StoryFilter filter) {
        return switch (filter) {
            case MORE_THAN_FIVE_WORDS -> stories.stream()
                    .filter(story -> wordCount(story) > WORD_COUNT_THRESHOLD)
                    .sorted(Comparator.comparing(Story::comments).reversed())
                    .toList();
            case FIVE_OR_FEWER_WORDS -> stories.stream()
                    .filter(story -> wordCount(story) <= WORD_COUNT_THRESHOLD)
                    .sorted(Comparator.comparing(Story::points).reversed())
                    .toList();
        };
    }

    private int wordCount(Story story) {
        return wordCountService.countWords(story.title());
    }
}
