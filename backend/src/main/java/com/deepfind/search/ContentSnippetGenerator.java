package com.deepfind.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContentSnippetGenerator {

    static final int MAX_SNIPPET_CHARACTERS = 240;
    private static final int LEADING_CONTEXT_CHARACTERS = 80;
    private static final Pattern QUERY_TERM = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ContentSnippetGenerator() {}

    public static Optional<SearchSnippet> generate(String source, String query) {
        if (source == null || source.isBlank() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        String compactSource = WHITESPACE.matcher(source).replaceAll(" ").trim();
        List<String> terms = queryTerms(query);
        List<Range> sourceMatches = matches(compactSource, terms);
        if (compactSource.isEmpty() || sourceMatches.isEmpty()) {
            return Optional.empty();
        }

        int firstMatch = sourceMatches.getFirst().start();
        int start = Math.max(0, firstMatch - LEADING_CONTEXT_CHARACTERS);
        int end = Math.min(compactSource.length(), start + MAX_SNIPPET_CHARACTERS);
        if (end == compactSource.length()) {
            start = Math.max(0, end - MAX_SNIPPET_CHARACTERS);
        }
        start = safeStart(compactSource, start);
        end = safeEnd(compactSource, end);

        String prefix = start > 0 ? "…" : "";
        String suffix = end < compactSource.length() ? "…" : "";
        String body = compactSource.substring(start, end);
        String text = prefix + body + suffix;
        int offset = prefix.length();
        List<SearchHighlight> highlights = new ArrayList<>();
        for (Range range : sourceMatches) {
            if (range.end() > start && range.start() < end) {
                int highlightStart = Math.max(range.start(), start) - start + offset;
                int highlightEnd = Math.min(range.end(), end) - start + offset;
                highlights.add(new SearchHighlight(highlightStart, highlightEnd));
            }
        }
        return Optional.of(new SearchSnippet(text, highlights));
    }

    private static List<String> queryTerms(String query) {
        LinkedHashMap<String, String> uniqueTerms = new LinkedHashMap<>();
        Matcher matcher = QUERY_TERM.matcher(query);
        while (matcher.find()) {
            String term = matcher.group();
            uniqueTerms.putIfAbsent(term.toLowerCase(Locale.ROOT), term);
        }
        return List.copyOf(uniqueTerms.values());
    }

    private static List<Range> matches(String source, List<String> terms) {
        List<Range> matches = new ArrayList<>();
        for (String term : terms) {
            Matcher matcher = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(source);
            while (matcher.find()) {
                matches.add(new Range(matcher.start(), matcher.end()));
            }
        }
        matches.sort(Comparator.comparingInt(Range::start).thenComparingInt(Range::end));
        return merge(matches);
    }

    private static List<Range> merge(List<Range> ranges) {
        List<Range> merged = new ArrayList<>();
        for (Range range : ranges) {
            if (merged.isEmpty() || range.start() >= merged.getLast().end()) {
                merged.add(range);
            } else {
                Range previous = merged.removeLast();
                merged.add(new Range(previous.start(), Math.max(previous.end(), range.end())));
            }
        }
        return List.copyOf(merged);
    }

    private static int safeStart(String value, int index) {
        return index > 0 && index < value.length() && Character.isLowSurrogate(value.charAt(index)) ? index - 1 : index;
    }

    private static int safeEnd(String value, int index) {
        return index > 0 && index < value.length() && Character.isHighSurrogate(value.charAt(index - 1))
                ? index - 1
                : index;
    }

    private record Range(int start, int end) {}
}
