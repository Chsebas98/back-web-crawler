package com.web.crawler.web_crawler.web;

import com.web.crawler.web_crawler.application.StoryService;
import com.web.crawler.web_crawler.domain.Story;
import com.web.crawler.web_crawler.domain.StoryFilter;
import com.web.crawler.web_crawler.web.dto.ApiResponse;
import com.web.crawler.web_crawler.web.exception.InvalidFilterException;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Exposes the crawler as a single read endpoint. The list of {@link Story}
 * is returned as-is inside the {@code result} field of {@link ApiResponse}:
 * Story is already a domain object shaped exactly like the required JSON
 * contract, so an extra mapping layer would add indirection without benefit.
 * This differs from persistence entities (see UsageEntity), which are
 * deliberately never returned here.
 *
 * Any failure (invalid filter, upstream crawling error, etc.) is not handled
 * here: it propagates to {@link com.web.crawler.web_crawler.web.exception.GlobalErrorWebExceptionHandler},
 * which wraps it in the same {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<Story>>>> getStories(@RequestParam("filter") String filter) {
        StoryFilter storyFilter = parseFilter(filter);
        return storyService.getStories(storyFilter)
                .map(stories -> ResponseEntity.ok(
                        ApiResponse.success(HttpStatus.OK.value(), "Stories retrieved", stories)));
    }

    /**
     * Rejects an unknown filter with a 4xx instead of silently defaulting to
     * one of the two supported filters, per the exercise's requirement.
     */
    private StoryFilter parseFilter(String value) {
        try {
            return StoryFilter.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidFilterException(
                    "Unsupported filter '%s'. Expected one of %s".formatted(value, Arrays.toString(StoryFilter.values())));
        }
    }
}
