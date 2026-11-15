package io.hotdealwave.server.search.service;

import java.util.*;
import java.util.regex.Pattern;

/** Tokenizes identifiers and Korean text for search and its model inputs. */
public final class Tokenizer {
    private Tokenizer() {}

    static final Pattern WORD = Pattern.compile("[A-Za-z0-9_.\\-/]+|[가-힣]+");

    /** Identifiers whole and by camelCase/snake_case parts, lowercased and once each per word; Korean in two-letter pieces. */
    public static List<String> tokens(String text) {
        var tokens = new ArrayList<String>();
        var matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (word.charAt(0) >= '가' && word.charAt(0) <= '힣') {
                if (word.length() == 1) tokens.add(word);
                for (int i = 0; i + 2 <= word.length(); i++) tokens.add(word.substring(i, i + 2));
                continue;
            }
            word = word.replaceAll("^[._\\-/]+|[._\\-/]+$", "");
            if (word.isEmpty()) continue;
            // A dotted or hyphenated name also yields each identifier in it whole, then by case parts.
            var pieces = new LinkedHashSet<String>();
            pieces.add(word.toLowerCase());
            for (var segment : word.split("[./:-]+")) pieces.add(segment.toLowerCase());
            for (var part : word.replaceAll("([a-z0-9])([A-Z])", "$1 $2").split("[^A-Za-z0-9]+")) pieces.add(part.toLowerCase());
            pieces.remove("");
            tokens.addAll(pieces);
        }
        return tokens;
    }

}
