package com.web.crawler.web_crawler.application;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Counts words in a title according to the exercise's exact rule:
 * only space-separated tokens count, and only if they contain at least one
 * letter or digit. A token made up entirely of punctuation/symbols (e.g. "-",
 * "---", "!") is not a word. A hyphen attached to letters (e.g.
 * "self-explained") stays a single word since splitting is done on
 * whitespace only, never on punctuation.
 *
 * Deliberately simple: a real NLP tokenizer would be overkill for this rule
 * and harder to explain/verify.
 */
@Service
public class WordCountService {

    /** Matches any Unicode letter or digit; used to reject punctuation-only tokens. */
    private static final Pattern ALPHANUMERIC = Pattern.compile("[\\p{L}\\p{N}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public int countWords(String title) {
        if (title == null) {
            return 0;
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String token : WHITESPACE.split(trimmed)) {
            if (ALPHANUMERIC.matcher(token).find()) {
                count++;
            }
        }
        return count;
    }
}
