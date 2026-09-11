package com.deepfind.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void doesNotResolveLocalExternalXmlEntities() throws Exception {
        String privateToken = "external-entity-private-token";
        Path privateFile = Files.writeString(root.resolve("private.txt"), privateToken);
        Path xml = Files.writeString(
                root.resolve("hostile.xml"),
                "<!DOCTYPE root [<!ENTITY external SYSTEM \"" + privateFile.toUri() + "\">]><root>&external;</root>");

        try {
            ParsedDocument document = parser.parse(xml, 10_000);
            assertThat(document.content()).doesNotContain(privateToken);
        } catch (DocumentParsingException expected) {
            assertThat(expected).isNotNull();
        }
    }

    @Test
    void doesNotFetchHttpExternalXmlEntities() throws Exception {
        String remoteToken = "http-external-entity-private-token";
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/entity", exchange -> {
            requests.incrementAndGet();
            byte[] response = remoteToken.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Path xml = Files.writeString(
                root.resolve("remote-entity.xml"),
                "<!DOCTYPE root [<!ENTITY external SYSTEM \"http://127.0.0.1:"
                        + server.getAddress().getPort()
                        + "/entity\">]><root>&external;</root>");

        try {
            try {
                ParsedDocument document = parser.parse(xml, 10_000);
                assertThat(document.content()).doesNotContain(remoteToken);
            } catch (DocumentParsingException expected) {
                assertThat(expected).isNotNull();
            }
            assertThat(requests).hasValue(0);
        } finally {
            server.stop(0);
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
