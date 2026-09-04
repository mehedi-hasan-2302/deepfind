package com.deepfind.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.config.DeepFindExtractionProperties;
import com.deepfind.extraction.BoundedContentExtractor;
import com.deepfind.extraction.TikaDocumentParser;
import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import com.deepfind.search.MetadataMatchType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentIndexingIntegrationTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void indexesSupportedContentAndKeepsMalformedDocumentMetadataSearchable() throws Exception {
        Path corpus = Files.createDirectories(temporaryDirectory.resolve("corpus"));
        Files.writeString(corpus.resolve("notes.txt"), "the launch codename is starling");
        writePdf(corpus.resolve("contract.pdf"), "customers may request a refund");
        writeDocx(corpus.resolve("resume.docx"), "salary expectation is negotiable");
        Files.writeString(corpus.resolve("broken.pdf"), "%PDF-1.7 malformed content");

        try (LuceneMetadataIndex index = new LuceneMetadataIndex(temporaryDirectory.resolve("index"));
                BoundedContentExtractor extractor = extractor()) {
            indexingService(index, extractor).indexRoot(corpus, ExclusionPolicy.defaults(), new DiscoveryObserver() {});

            assertContentMatch(index, "starling", "notes.txt");
            assertContentMatch(index, "refund", "contract.pdf");
            assertContentMatch(index, "negotiable", "resume.docx");
            assertThat(index.search("broken.pdf", 10)).singleElement().satisfies(result -> assertThat(
                            result.metadata().filename())
                    .isEqualTo("broken.pdf"));
        }
    }

    @Test
    void replacesChangedContentAndPersistsTheLatestTerms() throws Exception {
        Path corpus = Files.createDirectories(temporaryDirectory.resolve("corpus"));
        Path notes = Files.writeString(corpus.resolve("notes.txt"), "obsolete phrase cedar");
        Path indexPath = temporaryDirectory.resolve("index");

        try (LuceneMetadataIndex index = new LuceneMetadataIndex(indexPath);
                BoundedContentExtractor extractor = extractor()) {
            MetadataIndexingService service = indexingService(index, extractor);
            service.indexRoot(corpus, ExclusionPolicy.defaults(), new DiscoveryObserver() {});
            assertContentMatch(index, "cedar", "notes.txt");

            Files.writeString(notes, "replacement phrase juniper");
            service.indexRoot(corpus, ExclusionPolicy.defaults(), new DiscoveryObserver() {});
            assertThat(index.search("cedar", 10)).isEmpty();
            assertContentMatch(index, "juniper", "notes.txt");
        }

        try (LuceneMetadataIndex reopened = new LuceneMetadataIndex(indexPath)) {
            assertContentMatch(reopened, "juniper", "notes.txt");
        }
    }

    private static MetadataIndexingService indexingService(
            LuceneMetadataIndex index, BoundedContentExtractor extractor) {
        return new MetadataIndexingService(new FileSystemDiscoveryService(), index, extractor, extractionProperties());
    }

    private static BoundedContentExtractor extractor() {
        return new BoundedContentExtractor(new TikaDocumentParser(), extractionProperties());
    }

    private static DeepFindExtractionProperties extractionProperties() {
        return new DeepFindExtractionProperties(2_000_000, 100_000, Duration.ofSeconds(30), 2, 4);
    }

    private static void assertContentMatch(LuceneMetadataIndex index, String query, String filename) {
        assertThat(index.search(query, 10)).singleElement().satisfies(result -> {
            assertThat(result.metadata().filename()).isEqualTo(filename);
            assertThat(result.matchType()).isEqualTo(MetadataMatchType.CONTENT);
        });
    }

    private static void writeDocx(Path path, String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            try (var output = Files.newOutputStream(path)) {
                document.write(output);
            }
        }
    }

    private static void writePdf(Path path, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(path.toFile());
        }
    }
}
