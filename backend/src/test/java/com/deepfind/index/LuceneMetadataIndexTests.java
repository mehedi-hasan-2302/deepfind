package com.deepfind.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepfind.extraction.ExtractionResult;
import com.deepfind.extraction.ParsedDocument;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.FileSystemEntryKind;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.search.MetadataMatchType;
import com.deepfind.search.MetadataSearchFilters;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceneMetadataIndexTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void ranksAndExplainsFilenameAndPathMatches() {
        Path indexPath = temporaryDirectory.resolve("index");
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath)) {
            index.upsert(metadata(temporaryDirectory.resolve("archive/invoice/client-notes.txt"), 10));
            index.upsert(metadata(temporaryDirectory.resolve("documents/invoice.pdf"), 20));
            index.upsert(metadata(temporaryDirectory.resolve("documents/invoice-2026.pdf"), 30));
            index.commit();

            assertThat(index.search("invoice.pdf", 10)).first().satisfies(result -> {
                assertThat(result.metadata().filename()).isEqualTo("invoice.pdf");
                assertThat(result.matchType()).isEqualTo(MetadataMatchType.EXACT_FILENAME);
            });
            assertThat(index.search("invoice", 10))
                    .extracting(result -> result.matchType())
                    .contains(MetadataMatchType.FILENAME_PREFIX, MetadataMatchType.PATH);
            assertThat(index.search("archive client", 10)).singleElement().satisfies(result -> {
                assertThat(result.metadata().filename()).isEqualTo("client-notes.txt");
                assertThat(result.matchType()).isEqualTo(MetadataMatchType.PATH);
            });
        }
    }

    @Test
    void upsertReplacesTheDocumentAtTheSameNormalizedPath() {
        Path indexPath = temporaryDirectory.resolve("index");
        Path file = temporaryDirectory.resolve("invoice.txt");
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath)) {
            index.upsert(metadata(file, 10));
            index.upsert(metadata(file, 99));
            index.commit();

            assertThat(index.count()).isEqualTo(1);
            assertThat(index.search("invoice", 10))
                    .singleElement()
                    .extracting(result -> result.metadata().sizeBytes())
                    .isEqualTo(99L);
        }
    }

    @Test
    void deletesAnEntryByPath() {
        Path indexPath = temporaryDirectory.resolve("index");
        Path file = temporaryDirectory.resolve("obsolete.txt");
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath)) {
            index.upsert(metadata(file, 10));
            index.commit();

            index.delete(file);
            index.commit();

            assertThat(index.search("obsolete", 10)).isEmpty();
            assertThat(index.count()).isZero();
        }
    }

    @Test
    void committedIndexRemainsSearchableAfterReopen() {
        Path indexPath = temporaryDirectory.resolve("index");
        Path file = temporaryDirectory.resolve("deep/path/resume-final.docx");
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath)) {
            index.upsert(metadata(file, 42));
            index.commit();
        }

        try (LuceneMetadataIndex reopened = new LuceneMetadataIndex(indexPath)) {
            assertThat(reopened.search("resume final", 10))
                    .singleElement()
                    .extracting(result -> result.metadata().absolutePath())
                    .isEqualTo(file.toAbsolutePath().normalize());
        }
    }

    @Test
    void searchesPersistedContentWhileRankingFilenamesFirst() {
        Path indexPath = temporaryDirectory.resolve("index");
        FileMetadata contentMatch = metadata(temporaryDirectory.resolve("documents/contract.pdf"), 42);
        FileMetadata filenameMatch = metadata(temporaryDirectory.resolve("documents/refund-policy.txt"), 21);
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath)) {
            index.upsertContent(
                    contentMatch,
                    ExtractionResult.success(
                            new ParsedDocument("customers may request a refund", "application/pdf", false)));
            index.upsert(filenameMatch);
            index.commit();

            assertThat(index.search("refund", 10))
                    .extracting(result -> result.metadata().filename())
                    .containsExactly("refund-policy.txt", "contract.pdf");
            assertThat(index.search("refund", 10).get(1).matchType()).isEqualTo(MetadataMatchType.CONTENT);
            assertThat(index.search("refund", 10).get(1).snippet()).satisfies(snippet -> {
                assertThat(snippet.text()).contains("refund");
                assertThat(snippet.highlights()).singleElement().satisfies(highlight -> assertThat(
                                snippet.text().substring(highlight.start(), highlight.end()))
                        .isEqualTo("refund"));
            });
        }

        try (LuceneMetadataIndex reopened = new LuceneMetadataIndex(indexPath)) {
            assertThat(reopened.search("customers", 10)).singleElement().satisfies(result -> {
                assertThat(result.metadata().filename()).isEqualTo("contract.pdf");
                assertThat(result.matchType()).isEqualTo(MetadataMatchType.CONTENT);
            });
        }
    }

    @Test
    void quotedPhrasesRequireAdjacentTermsAndKeepFilenameRanking() {
        Path indexPath = temporaryDirectory.resolve("phrase-index");
        FileMetadata filenameMatch = metadata(temporaryDirectory.resolve("annual-budget-report.txt"), 10);
        FileMetadata phraseMatch = metadata(temporaryDirectory.resolve("board-minutes.txt"), 20);
        FileMetadata separatedTerms = metadata(temporaryDirectory.resolve("planning-notes.txt"), 30);
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath)) {
            index.upsert(filenameMatch);
            index.upsertContent(
                    phraseMatch,
                    ExtractionResult.success(
                            new ParsedDocument("The annual budget report is confidential.", "text/plain", false)));
            index.upsertContent(
                    separatedTerms,
                    ExtractionResult.success(new ParsedDocument(
                            "The annual budget forecast precedes the report.", "text/plain", false)));
            index.commit();

            assertThat(index.search("\"annual budget report\"", 10))
                    .extracting(result -> result.metadata().filename())
                    .containsExactly("annual-budget-report.txt", "board-minutes.txt");
            assertThat(index.search("\"annual budget report\"", 10).get(1).matchType())
                    .isEqualTo(MetadataMatchType.EXACT_PHRASE);
            assertThat(index.search("\"annual budget report\" confidential", 10))
                    .singleElement()
                    .extracting(result -> result.metadata().filename())
                    .isEqualTo("board-minutes.txt");
        }
    }

    @Test
    void unmatchedAndEmptyQuotesFallBackSafely() {
        Path indexPath = temporaryDirectory.resolve("quote-fallback-index");
        FileMetadata content = metadata(temporaryDirectory.resolve("notes.txt"), 10);
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath)) {
            index.upsertContent(
                    content,
                    ExtractionResult.success(new ParsedDocument("annual planning report", "text/plain", false)));
            index.commit();

            assertThat(index.search("\"annual report", 10))
                    .singleElement()
                    .extracting(result -> result.metadata().filename())
                    .isEqualTo("notes.txt");
            assertThat(index.search("\"\"", 10)).isEmpty();
        }
    }

    @Test
    void filtersByKindExtensionModifiedTimeAndSizeWithoutChangingRanking() {
        Path indexPath = temporaryDirectory.resolve("filter-index");
        FileMetadata recentPdf = metadata(
                temporaryDirectory.resolve("reports/invoice-current.pdf"),
                5L * 1024 * 1024,
                Instant.parse("2026-09-01T12:00:00Z"),
                FileSystemEntryKind.FILE);
        FileMetadata oldDocx = metadata(
                temporaryDirectory.resolve("reports/invoice-archive.docx"),
                20L * 1024 * 1024,
                Instant.parse("2025-01-01T12:00:00Z"),
                FileSystemEntryKind.FILE);
        FileMetadata directory = metadata(
                temporaryDirectory.resolve("invoice-folder"),
                0,
                Instant.parse("2026-09-02T12:00:00Z"),
                FileSystemEntryKind.DIRECTORY);
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath)) {
            index.upsert(recentPdf);
            index.upsert(oldDocx);
            index.upsert(directory);
            index.commit();

            assertThat(index.searchPage(
                                    "invoice",
                                    10,
                                    new MetadataSearchFilters(FileSystemEntryKind.FILE, ".PDF", null, null, null, null))
                            .results())
                    .singleElement()
                    .extracting(result -> result.metadata().filename())
                    .isEqualTo("invoice-current.pdf");
            assertThat(index.searchPage(
                                    "invoice",
                                    10,
                                    new MetadataSearchFilters(
                                            null,
                                            null,
                                            Instant.parse("2026-08-01T00:00:00Z"),
                                            Instant.parse("2026-09-01T23:59:59Z"),
                                            null,
                                            null))
                            .results())
                    .singleElement()
                    .extracting(result -> result.metadata().filename())
                    .isEqualTo("invoice-current.pdf");
            assertThat(index.searchPage(
                                    "invoice",
                                    10,
                                    new MetadataSearchFilters(null, null, null, null, 10L * 1024 * 1024, null))
                            .results())
                    .singleElement()
                    .extracting(result -> result.metadata().filename())
                    .isEqualTo("invoice-archive.docx");
            assertThat(index.searchPage(
                                    "invoice",
                                    10,
                                    new MetadataSearchFilters(
                                            FileSystemEntryKind.DIRECTORY, null, null, null, null, null))
                            .results())
                    .singleElement()
                    .extracting(result -> result.metadata().filename())
                    .isEqualTo("invoice-folder");
        }
    }

    @Test
    void emptyQueriesAreSafeAndLimitsAreBounded() {
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(temporaryDirectory.resolve("index"))) {
            assertThat(index.search("   ", 10)).isEmpty();
            assertThatThrownBy(() -> index.search("anything", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 1 and 1000");
        }
    }

    @Test
    void punctuationOnlyQueriesDoNotExposeParserErrors() {
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(temporaryDirectory.resolve("index"))) {
            index.upsert(metadata(temporaryDirectory.resolve("(draft).txt"), 10));
            index.commit();

            assertThat(index.search("(", 10))
                    .singleElement()
                    .extracting(result -> result.metadata().filename())
                    .isEqualTo("(draft).txt");
        }
    }

    @Test
    void rejectsAnIncompatibleSchemaVersionExplicitly() throws Exception {
        Path indexPath = temporaryDirectory.resolve("incompatible-index");
        try (Directory directory = FSDirectory.open(indexPath);
                MetadataTextAnalyzer analyzer = new MetadataTextAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.setLiveCommitData(
                    Map.of(LuceneIndexSchema.VERSION_KEY, "999").entrySet());
            writer.commit();
        }

        assertThatThrownBy(() -> new LuceneMetadataIndex(indexPath))
                .isInstanceOf(IndexSchemaMismatchException.class)
                .hasMessageContaining("expected 3")
                .hasMessageContaining("999");
    }

    private static FileMetadata metadata(Path path, long size) {
        return metadata(path, size, Instant.parse("2026-09-01T10:15:30Z"), FileSystemEntryKind.FILE);
    }

    private static FileMetadata metadata(Path path, long size, Instant modifiedAt, FileSystemEntryKind kind) {
        Path absolutePath = path.toAbsolutePath().normalize();
        String filename = absolutePath.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
        return new FileMetadata(
                absolutePath,
                PathNormalizer.searchKey(absolutePath),
                filename,
                extension,
                kind,
                size,
                modifiedAt,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
