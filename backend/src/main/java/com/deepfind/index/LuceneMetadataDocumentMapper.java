package com.deepfind.index;

import com.deepfind.extraction.ExtractionResult;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.FileSystemEntryKind;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

final class LuceneMetadataDocumentMapper {

    Document toDocument(FileMetadata metadata, Instant indexedAt) {
        return toDocument(metadata, indexedAt, null);
    }

    Document toDocument(FileMetadata metadata, Instant indexedAt, ExtractionResult extraction) {
        Document document = new Document();
        document.add(new StringField(LuceneIndexSchema.PATH_KEY, metadata.normalizedPath(), StringField.Store.YES));
        document.add(new StoredField(
                LuceneIndexSchema.ABSOLUTE_PATH, metadata.absolutePath().toString()));
        document.add(new TextField(LuceneIndexSchema.FILENAME, metadata.filename(), TextField.Store.YES));
        document.add(new StringField(
                LuceneIndexSchema.FILENAME_EXACT, metadata.filename().toLowerCase(Locale.ROOT), StringField.Store.NO));
        document.add(new TextField(
                LuceneIndexSchema.PATH_TEXT, metadata.absolutePath().toString(), TextField.Store.NO));
        if (extraction == null) {
            document.add(new StringField(LuceneIndexSchema.EXTRACTION_STATUS, "NOT_ATTEMPTED", StringField.Store.YES));
        } else {
            document.add(new StringField(
                    LuceneIndexSchema.EXTRACTION_STATUS, extraction.status().name(), StringField.Store.YES));
            if (!extraction.reason().isEmpty()) {
                document.add(new StoredField(LuceneIndexSchema.EXTRACTION_REASON, extraction.reason()));
            }
            if (!extraction.content().isEmpty()) {
                document.add(new TextField(LuceneIndexSchema.CONTENT, extraction.content(), TextField.Store.NO));
            }
        }
        document.add(new StringField(LuceneIndexSchema.EXTENSION, metadata.extension(), StringField.Store.YES));
        document.add(new StringField(LuceneIndexSchema.KIND, metadata.kind().name(), StringField.Store.YES));
        addSortableLong(document, LuceneIndexSchema.SIZE_BYTES, metadata.sizeBytes());
        addSortableLong(
                document, LuceneIndexSchema.MODIFIED_AT, metadata.modifiedAt().toEpochMilli());
        addSortableLong(
                document, LuceneIndexSchema.CREATED_AT, metadata.createdAt().toEpochMilli());
        addSortableLong(document, LuceneIndexSchema.LAST_INDEXED_AT, indexedAt.toEpochMilli());
        return document;
    }

    FileMetadata fromDocument(Document document) {
        return new FileMetadata(
                Path.of(document.get(LuceneIndexSchema.ABSOLUTE_PATH)),
                document.get(LuceneIndexSchema.PATH_KEY),
                document.get(LuceneIndexSchema.FILENAME),
                document.get(LuceneIndexSchema.EXTENSION),
                FileSystemEntryKind.valueOf(document.get(LuceneIndexSchema.KIND)),
                longValue(document, LuceneIndexSchema.SIZE_BYTES),
                Instant.ofEpochMilli(longValue(document, LuceneIndexSchema.MODIFIED_AT)),
                Instant.ofEpochMilli(longValue(document, LuceneIndexSchema.CREATED_AT)));
    }

    private static void addSortableLong(Document document, String field, long value) {
        document.add(new LongPoint(field, value));
        document.add(new NumericDocValuesField(field, value));
        document.add(new StoredField(field, value));
    }

    private static long longValue(Document document, String field) {
        return document.getField(field).numericValue().longValue();
    }
}
