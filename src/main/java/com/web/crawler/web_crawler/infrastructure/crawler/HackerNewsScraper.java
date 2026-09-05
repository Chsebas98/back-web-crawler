package com.web.crawler.web_crawler.infrastructure.crawler;

import com.web.crawler.web_crawler.domain.Story;
import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsParsingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Converts the raw Hacker News homepage HTML into a list of {@link Story}
 * domain objects.
 *
 * This class is the only place in the application that knows about Hacker
 * News' HTML structure (rows with class "athing" followed by a sibling
 * "subtext" row). Everything downstream works with {@link Story} only, so a
 * future Hacker News markup change is isolated to this one class.
 */
@Component
public class HackerNewsScraper {

    private static final Logger log = LoggerFactory.getLogger(HackerNewsScraper.class);

    /** The exercise only requires the first 30 entries. */
    static final int EXPECTED_STORY_COUNT = 30;

    private static final String STORY_ROW_SELECTOR = "tr.athing";
    private static final String TITLE_SELECTOR = "span.titleline a";
    private static final String RANK_SELECTOR = "span.rank";
    private static final String SCORE_SELECTOR = "span.score";
    private static final String COMMENTS_LINK_SELECTOR = "a[href^=item?id=]";

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final HackerNewsClient client;

    public HackerNewsScraper(HackerNewsClient client) {
        this.client = client;
    }

    /**
     * Fetches and parses the first {@value #EXPECTED_STORY_COUNT} Hacker News
     * front-page stories.
     */
    public Mono<List<Story>> scrapeTopStories() {
        return client.fetchHomepageHtml()
                .map(this::parseStories)
                .doOnNext(stories -> log.info("Parsed {} Hacker News stories", stories.size()));
    }

    private List<Story> parseStories(String html) {
        Document document = Jsoup.parse(html);
        Elements rows = document.select(STORY_ROW_SELECTOR);

        List<Story> stories = new ArrayList<>(EXPECTED_STORY_COUNT);
        for (Element row : rows) {
            if (stories.size() == EXPECTED_STORY_COUNT) {
                break;
            }
            stories.add(parseStory(row));
        }

        if (stories.size() < EXPECTED_STORY_COUNT) {
            throw new HackerNewsParsingException(
                    "Expected %d Hacker News stories but found only %d"
                            .formatted(EXPECTED_STORY_COUNT, stories.size()));
        }
        return stories;
    }

    private Story parseStory(Element row) {
        Element titleAnchor = row.selectFirst(TITLE_SELECTOR);
        if (titleAnchor == null) {
            throw new HackerNewsParsingException(
                    "Missing title for Hacker News row id=" + row.id());
        }

        // The rank/points/comments row is the sibling <tr> immediately after
        // the "athing" row. It may be absent for a malformed page.
        Element subtext = row.nextElementSibling();

        return new Story(
                parseRank(row, titleAnchor),
                titleAnchor.text(),
                parsePoints(subtext),
                parseComments(subtext));
    }

    private int parseRank(Element row, Element titleAnchor) {
        Element rankElement = row.selectFirst(RANK_SELECTOR);
        if (rankElement == null) {
            throw new HackerNewsParsingException(
                    "Missing rank for Hacker News story \"" + titleAnchor.text() + "\"");
        }
        return parseLeadingInt(rankElement.text())
                .orElseThrow(() -> new HackerNewsParsingException(
                        "Unable to parse rank \"" + rankElement.text() + "\""));
    }

    /**
     * Points are absent for job postings (they cannot be voted on). Rather
     * than treating that as corrupted data, it is deliberately defaulted to 0
     * since a missing vote count is a legitimate state, not a parsing error.
     */
    private int parsePoints(Element subtext) {
        if (subtext == null) {
            return 0;
        }
        Element scoreElement = subtext.selectFirst(SCORE_SELECTOR);
        if (scoreElement == null) {
            return 0;
        }
        return parseLeadingInt(scoreElement.text()).orElse(0);
    }

    /**
     * Comments are rendered as "N&nbsp;comments", or "discuss" when there are
     * none yet. Job postings have no comments link at all.
     *
     * The "N comments"/"discuss" anchor and the age timestamp anchor
     * ("2 hours ago") both link to "item?id=...", so href alone cannot tell
     * them apart. Matching is done on the link text instead, scanning from
     * the end since the comments link is always the last one when present.
     */
    private int parseComments(Element subtext) {
        if (subtext == null) {
            return 0;
        }
        Elements links = subtext.select(COMMENTS_LINK_SELECTOR);
        for (int i = links.size() - 1; i >= 0; i--) {
            String text = links.get(i).text().trim();
            if (text.equalsIgnoreCase("discuss")) {
                return 0;
            }
            if (text.toLowerCase(Locale.ROOT).contains("comment")) {
                return parseLeadingInt(text).orElse(0);
            }
        }
        return 0;
    }

    private Optional<Integer> parseLeadingInt(String text) {
        Matcher matcher = DIGITS.matcher(text);
        return matcher.find() ? Optional.of(Integer.parseInt(matcher.group())) : Optional.empty();
    }
}
