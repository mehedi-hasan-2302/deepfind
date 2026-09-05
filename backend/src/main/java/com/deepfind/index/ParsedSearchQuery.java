package com.deepfind.index;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

record ParsedSearchQuery(String literalText, String unquotedText, List<String> phrases) {

    private static final int MINIMUM_FUZZY_TERM_LENGTH = 4;
    private static final int MAXIMUM_FUZZY_TERM_LENGTH = 32;

    static ParsedSearchQuery parse(String input) {
        String text = input.trim();
        List<String> phrases = new ArrayList<>();
        StringBuilder unquoted = new StringBuilder();
        StringBuilder phrase = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                if (quoted) {
                    addPhrase(phrases, phrase);
                    phrase.setLength(0);
                    unquoted.append(' ');
                }
                quoted = !quoted;
            } else if (quoted) {
                phrase.append(character);
            } else {
                unquoted.append(character);
            }
        }
        if (quoted) {
            return new ParsedSearchQuery(
                    normalize(text.replace('"', ' ')), normalize(text.replace('"', ' ')), List.of());
        }
        String ordinary = normalize(unquoted.toString());
        String literal = normalize(text.replace('"', ' '));
        return new ParsedSearchQuery(literal, ordinary, List.copyOf(phrases));
    }

    private static void addPhrase(List<String> phrases, StringBuilder phrase) {
        String normalized = normalize(phrase.toString());
        if (!normalized.isEmpty()) {
            phrases.add(normalized);
        }
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    Optional<String> fuzzyFilenameTerm() {
        if (!phrases.isEmpty()
                || !literalText.equals(unquotedText)
                || literalText.length() < MINIMUM_FUZZY_TERM_LENGTH
                || literalText.length() > MAXIMUM_FUZZY_TERM_LENGTH
                || !literalText.matches("[\\p{L}\\p{N}]+")) {
            return Optional.empty();
        }
        return Optional.of(literalText.toLowerCase(Locale.ROOT));
    }
}
