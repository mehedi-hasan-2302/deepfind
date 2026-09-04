package com.deepfind.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.config.DeepFindExtractionProperties;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedContentExtractorTests {

    @TempDir
    Path root;

    @Test
    void extractsSupportedTextAndKeepsStructuredSuccessMetadata() throws IOException {
        Path file = Files.writeString(root.resolve("source.java"), "class SearchableDocument {}");
        try (BoundedContentExtractor extractor = extractor(
                parserReturning("text/plain", "class SearchableDocument {}"), 1_000, 1_000, Duration.ofSeconds(2))) {
            ExtractionResult result = extractor.extract(file);

            assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
            assertThat(result.content()).contains("SearchableDocument");
            assertThat(result.mediaType()).startsWith("text/");
            assertThat(result.reason()).isEmpty();
        }
    }

    @Test
    void skipsUnsupportedAndOversizedFilesBeforeCallingTheParser() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        DocumentParser parser = countingParser(calls);
        Path unsupported = Files.write(root.resolve("image.png"), new byte[] {1, 2, 3});
        Path oversized = Files.writeString(root.resolve("large.txt"), "far too much text");
        try (BoundedContentExtractor extractor = extractor(parser, 4, 1_000, Duration.ofSeconds(1))) {
            assertThat(extractor.extract(unsupported).status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
            assertThat(extractor.extract(oversized).status()).isEqualTo(ExtractionStatus.SKIPPED_TOO_LARGE);
            assertThat(calls).hasValue(0);
        }
    }

    @Test
    void rejectsAnExtensionWhoseDetectedMediaTypeDoesNotMatch() throws IOException {
        Path disguisedExecutable = Files.write(root.resolve("disguised.txt"), new byte[] {'M', 'Z', 0, 0});
        DocumentParser parser = parserReturning("application/x-msdownload", "ignored");
        try (BoundedContentExtractor extractor = extractor(parser, 1_000, 1_000, Duration.ofSeconds(1))) {
            ExtractionResult result = extractor.extract(disguisedExecutable);

            assertThat(result.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
            assertThat(result.reason()).isEqualTo("MEDIA_TYPE_NOT_SUPPORTED");
        }
    }

    @Test
    void categorizesPermissionAndParserFailuresWithoutLeakingExceptionMessages() throws IOException {
        Path file = Files.writeString(root.resolve("private.txt"), "private");
        DocumentParser denied = new StubParser() {
            @Override
            public String detectMediaType(Path path) throws IOException {
                throw new AccessDeniedException(path.toString(), null, "sensitive operating-system detail");
            }
        };
        DocumentParser malformed = new StubParser() {
            @Override
            public ParsedDocument parse(Path path, int characterLimit) throws DocumentParsingException {
                throw new DocumentParsingException(new IllegalStateException("sensitive parser detail"));
            }
        };

        try (BoundedContentExtractor deniedExtractor = extractor(denied, 1_000, 1_000, Duration.ofSeconds(1));
                BoundedContentExtractor malformedExtractor =
                        extractor(malformed, 1_000, 1_000, Duration.ofSeconds(1))) {
            ExtractionResult deniedResult = deniedExtractor.extract(file);
            ExtractionResult malformedResult = malformedExtractor.extract(file);

            assertThat(deniedResult.status()).isEqualTo(ExtractionStatus.PERMISSION_DENIED);
            assertThat(deniedResult.reason()).isEqualTo("FILE_NOT_READABLE");
            assertThat(malformedResult.status()).isEqualTo(ExtractionStatus.PARSE_ERROR);
            assertThat(malformedResult.reason()).isEqualTo("DOCUMENT_PARSE_FAILED");
            assertThat(deniedResult.toString()).doesNotContain("sensitive");
            assertThat(malformedResult.toString()).doesNotContain("sensitive");
        }
    }

    @Test
    void containsMalformedPdfAsAStructuredParseError() throws IOException {
        Path pdf = Files.writeString(root.resolve("broken.pdf"), "%PDF-1.7 definitely not a valid document");
        try (BoundedContentExtractor extractor =
                extractor(new TikaDocumentParser(), 1_000, 10_000, Duration.ofSeconds(10))) {
            ExtractionResult result = extractor.extract(pdf);

            assertThat(result.status()).isEqualTo(ExtractionStatus.PARSE_ERROR);
            assertThat(result.reason()).isEqualTo("DOCUMENT_PARSE_FAILED");
            assertThat(result.content()).isEmpty();
        }
    }

    @Test
    void cancelsExtractionWhenTheDeadlineExpires() throws IOException {
        Path file = Files.writeString(root.resolve("slow.txt"), "slow");
        DocumentParser slowParser = new StubParser() {
            @Override
            public ParsedDocument parse(Path path, int characterLimit) throws IOException {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", exception);
                }
                return new ParsedDocument("late", "text/plain", false);
            }
        };
        try (BoundedContentExtractor extractor = extractor(slowParser, 1_000, 1_000, Duration.ofMillis(20))) {
            ExtractionResult result = extractor.extract(file);

            assertThat(result.status()).isEqualTo(ExtractionStatus.TIMEOUT);
            assertThat(result.reason()).isEqualTo("EXTRACTION_DEADLINE_EXCEEDED");
        }
    }

    private static BoundedContentExtractor extractor(
            DocumentParser parser, long maxBytes, int maxCharacters, Duration timeout) {
        return new BoundedContentExtractor(
                parser, new DeepFindExtractionProperties(maxBytes, maxCharacters, timeout, 1, 2));
    }

    private static DocumentParser countingParser(AtomicInteger calls) {
        return new StubParser() {
            @Override
            public String detectMediaType(Path path) {
                calls.incrementAndGet();
                return "text/plain";
            }
        };
    }

    private static DocumentParser parserReturning(String mediaType, String content) {
        return new StubParser() {
            @Override
            public String detectMediaType(Path path) {
                return mediaType;
            }

            @Override
            public ParsedDocument parse(Path path, int characterLimit) {
                return new ParsedDocument(content, mediaType, false);
            }
        };
    }

    private abstract static class StubParser implements DocumentParser {

        @Override
        public String detectMediaType(Path path) throws IOException {
            return "text/plain";
        }

        @Override
        public ParsedDocument parse(Path path, int characterLimit) throws IOException, DocumentParsingException {
            return new ParsedDocument("content", "text/plain", false);
        }
    }
}
