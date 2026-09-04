package com.deepfind.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public final class TikaDocumentParser implements DocumentParser {

    private final ThreadLocal<DefaultDetector> detectors = ThreadLocal.withInitial(DefaultDetector::new);
    private final ThreadLocal<AutoDetectParser> parsers = ThreadLocal.withInitial(AutoDetectParser::new);

    @Override
    public String detectMediaType(Path path) throws IOException {
        Metadata metadata = metadata(path);
        try (TikaInputStream input = TikaInputStream.get(path, metadata)) {
            return detectors.get().detect(input, metadata).toString();
        }
    }

    @Override
    public ParsedDocument parse(Path path, int characterLimit) throws IOException, DocumentParsingException {
        Metadata metadata = metadata(path);
        BodyContentHandler handler = new BodyContentHandler(characterLimit);
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, NoEmbeddedDocuments.INSTANCE);
        try (InputStream input = Files.newInputStream(path)) {
            parsers.get().parse(input, handler, metadata, context);
            return new ParsedDocument(handler.toString(), contentType(metadata), false);
        } catch (SAXException exception) {
            if (WriteLimitReachedException.isWriteLimitReached(exception)) {
                return new ParsedDocument(handler.toString(), contentType(metadata), true);
            }
            throw new DocumentParsingException(exception);
        } catch (org.apache.tika.exception.TikaException exception) {
            throw new DocumentParsingException(exception);
        }
    }

    private static Metadata metadata(Path path) {
        Metadata metadata = new Metadata();
        if (path.getFileName() != null) {
            metadata.set(
                    TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        }
        return metadata;
    }

    private static String contentType(Metadata metadata) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        return contentType == null ? "application/octet-stream" : contentType;
    }

    private enum NoEmbeddedDocuments implements EmbeddedDocumentExtractor {
        INSTANCE;

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return false;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) {
            // Nested attachments and archives are deliberately excluded from the initial extraction policy.
        }
    }
}
