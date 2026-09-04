package com.deepfind.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TikaDocumentParserTests {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    @TempDir
    Path root;

    @Test
    void extractsPlainTextAndHonorsTheCharacterLimit() throws Exception {
        Path text = Files.writeString(root.resolve("notes.txt"), "alpha beta gamma delta");

        assertThat(parser.detectMediaType(text)).startsWith("text/plain");
        ParsedDocument document = parser.parse(text, 10);

        assertThat(document.content()).contains("alpha beta").hasSizeLessThanOrEqualTo(10);
        assertThat(document.mediaType()).startsWith("text/plain");
        assertThat(document.truncated()).isTrue();
    }

    @Test
    void extractsCommonSourceCodeAsText() throws Exception {
        Path source = Files.writeString(root.resolve("SearchService.java"), "class SearchService { void search() {} }");

        assertThat(parser.detectMediaType(source)).startsWith("text/");
        assertThat(parser.parse(source, 10_000).content()).contains("SearchService", "search");
    }

    @Test
    void extractsTextFromDocx() throws Exception {
        Path docx = root.resolve("contract.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("refunds are available within thirty days");
            try (var output = Files.newOutputStream(docx)) {
                document.write(output);
            }
        }

        assertThat(parser.detectMediaType(docx))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(parser.parse(docx, 10_000).content()).contains("refunds are available within thirty days");
    }

    @Test
    void extractsTextFromPdf() throws Exception {
        Path pdf = root.resolve("invoice.pdf");
        writePdf(pdf, "invoice payment deadline is Friday");

        assertThat(parser.detectMediaType(pdf)).isEqualTo("application/pdf");
        assertThat(parser.parse(pdf, 10_000).content()).contains("invoice payment deadline is Friday");
    }

    @Test
    void containsMalformedPdfFailuresInsideTheParserBoundary() throws IOException {
        Path pdf = Files.writeString(root.resolve("broken.pdf"), "%PDF-1.7 definitely not a valid document");

        assertThatThrownBy(() -> parser.parse(pdf, 10_000)).isInstanceOf(DocumentParsingException.class);
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
