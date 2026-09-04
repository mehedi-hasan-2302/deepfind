package com.deepfind.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.FileSystemEntryKind;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.search.MetadataMatchType;
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
                .hasMessageContaining("expected 1")
                .hasMessageContaining("999");
    }

    private static FileMetadata metadata(Path path, long size) {
        Path absolutePath = path.toAbsolutePath().normalize();
        String filename = absolutePath.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
        return new FileMetadata(
                absolutePath,
                PathNormalizer.searchKey(absolutePath),
                filename,
                extension,
                FileSystemEntryKind.FILE,
                size,
                Instant.parse("2026-09-01T10:15:30Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
