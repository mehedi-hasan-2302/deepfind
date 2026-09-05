package com.deepfind.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.extraction.ExtractionResult;
import com.deepfind.extraction.ParsedDocument;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.FileSystemEntryKind;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.search.MetadataMatchType;
import com.deepfind.search.MetadataSearchResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchQualityEvaluationTests {

    @TempDir
    Path temporaryDirectory;

    private LuceneMetadataIndex index;

    @BeforeEach
    void buildEvaluationCorpus() {
        index = new LuceneMetadataIndex(temporaryDirectory.resolve("evaluation-index"));
        add("legal/contract.pdf", "Customers may request a refund within thirty days.");
        add("finance/receipt.txt", "Vendor invoice 8391 was paid in full.");
        add("cloud/cloud-notes.md", "The AWS cancellation procedure requires an account owner.");
        add("career/resume.docx", "The candidate's salary expectation is negotiable.");
        add("meetings/meeting-notes.txt", "Discussed contract renewal and the cloud budget.");

        add("finance/invoice-2026.pdf", "Quarterly billing statement.");
        add("finance/customer-invoice-notes.txt", "Customer billing follow-up.");
        add("archive/invoice/client-notes.txt", "Archived client correspondence.");
        index.commit();
    }

    @Test
    void commonQueriesReturnTheExpectedTopResult() {
        assertTopResult("refund", "contract.pdf", MetadataMatchType.CONTENT);
        assertTopResult("AWS cancellation", "cloud-notes.md", MetadataMatchType.CONTENT);
        assertTopResult("salary expectation", "resume.docx", MetadataMatchType.CONTENT);
        assertTopResult("invoice", "invoice-2026.pdf", MetadataMatchType.FILENAME_PREFIX);
        assertTopResult("resume", "resume.docx", MetadataMatchType.FILENAME_PREFIX);
    }

    @Test
    void filenamePrefixTokenPathAndContentTiersStayOrdered() {
        List<MetadataSearchResult> results = index.search("invoice", 10);

        assertThat(results)
                .extracting(result -> result.metadata().filename())
                .containsSubsequence(
                        "invoice-2026.pdf", "customer-invoice-notes.txt", "client-notes.txt", "receipt.txt");
        assertThat(results)
                .extracting(MetadataSearchResult::matchType)
                .containsSubsequence(
                        MetadataMatchType.FILENAME_PREFIX,
                        MetadataMatchType.FILENAME,
                        MetadataMatchType.PATH,
                        MetadataMatchType.CONTENT);
    }

    @org.junit.jupiter.api.AfterEach
    void closeIndex() {
        index.close();
    }

    private void assertTopResult(String query, String filename, MetadataMatchType matchType) {
        assertThat(index.search(query, 10)).first().satisfies(result -> {
            assertThat(result.metadata().filename()).isEqualTo(filename);
            assertThat(result.matchType()).isEqualTo(matchType);
        });
    }

    private void add(String relativePath, String content) {
        Path path = temporaryDirectory.resolve("corpus").resolve(relativePath);
        Path absolutePath = path.toAbsolutePath().normalize();
        String filename = absolutePath.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
        FileMetadata metadata = new FileMetadata(
                absolutePath,
                PathNormalizer.searchKey(absolutePath),
                filename,
                extension,
                FileSystemEntryKind.FILE,
                content.length(),
                Instant.parse("2026-09-01T10:15:30Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
        index.upsertContent(metadata, ExtractionResult.success(new ParsedDocument(content, "text/plain", false)));
    }
}
