package com.deepfind.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentSnippetGeneratorTests {

    @Test
    void selectsBoundedContextAndReturnsUtf16SafeHighlightOffsets() {
        String source =
                "😀 ".repeat(100) + "Customers may request a REFUND within thirty days. <script>alert(1)</script>";

        SearchSnippet snippet =
                ContentSnippetGenerator.generate(source, "refund").orElseThrow();

        assertThat(snippet.text()).startsWith("…").hasSizeLessThanOrEqualTo(242);
        assertThat(snippet.highlights()).singleElement().satisfies(highlight -> {
            assertThat(snippet.text().substring(highlight.start(), highlight.end()))
                    .isEqualTo("REFUND");
        });
        assertThat(snippet.text()).contains("<script>");
    }

    @Test
    void compactsWhitespaceAndHighlightsEachQueryTermInOrder() {
        SearchSnippet snippet = ContentSnippetGenerator.generate(
                        "Customers\n\nmay request a refund\tunder the return policy.", "refund policy")
                .orElseThrow();

        assertThat(snippet.text()).doesNotContain("\n", "\t");
        assertThat(snippet.highlights())
                .extracting(highlight -> snippet.text().substring(highlight.start(), highlight.end()))
                .containsExactly("refund", "policy");
    }

    @Test
    void omitsASnippetWhenNoQueryTermExistsInTheStoredSource() {
        assertThat(ContentSnippetGenerator.generate("short preview", "missing")).isEmpty();
        assertThat(ContentSnippetGenerator.generate("", "missing")).isEmpty();
    }
}
