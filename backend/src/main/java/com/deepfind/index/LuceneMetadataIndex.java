package com.deepfind.index;

import com.deepfind.extraction.ExtractionResult;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.search.ContentSnippetGenerator;
import com.deepfind.search.MetadataMatchType;
import com.deepfind.search.MetadataSearchFilters;
import com.deepfind.search.MetadataSearchPage;
import com.deepfind.search.MetadataSearchResult;
import com.deepfind.search.SearchSnippet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.QueryBuilder;

public final class LuceneMetadataIndex implements AutoCloseable {

    private static final int MAX_RESULT_LIMIT = 1_000;

    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;
    private final SearcherManager searcherManager;
    private final LuceneMetadataDocumentMapper mapper = new LuceneMetadataDocumentMapper();
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();

    public LuceneMetadataIndex(Path indexPath) {
        this(indexPath, Clock.systemUTC());
    }

    LuceneMetadataIndex(Path indexPath, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Directory openedDirectory = null;
        Analyzer openedAnalyzer = null;
        IndexWriter openedWriter = null;
        SearcherManager openedSearcherManager = null;
        try {
            Path absoluteIndexPath = PathNormalizer.absolute(indexPath);
            openedDirectory = FSDirectory.open(absoluteIndexPath);
            validateSchema(openedDirectory);
            openedAnalyzer = new MetadataTextAnalyzer();
            openedWriter = new IndexWriter(openedDirectory, new IndexWriterConfig(openedAnalyzer));
            openedWriter.setLiveCommitData(Map.of(LuceneIndexSchema.VERSION_KEY, LuceneIndexSchema.VERSION)
                    .entrySet());
            openedWriter.commit();
            openedSearcherManager = new SearcherManager(openedWriter, new SearcherFactory());
        } catch (IOException exception) {
            IOUtils.closeWhileHandlingException(openedSearcherManager, openedWriter, openedAnalyzer, openedDirectory);
            throw new IndexAccessException("DeepFind could not open the local search index.", exception);
        } catch (RuntimeException exception) {
            IOUtils.closeWhileHandlingException(openedSearcherManager, openedWriter, openedAnalyzer, openedDirectory);
            throw exception;
        }
        directory = openedDirectory;
        analyzer = openedAnalyzer;
        writer = openedWriter;
        searcherManager = openedSearcherManager;
    }

    public void upsert(FileMetadata metadata) {
        upsert(metadata, null);
    }

    public void upsertContent(FileMetadata metadata, ExtractionResult extraction) {
        Objects.requireNonNull(extraction, "extraction must not be null");
        upsert(metadata, extraction);
    }

    private void upsert(FileMetadata metadata, ExtractionResult extraction) {
        ensureOpen();
        Objects.requireNonNull(metadata, "metadata must not be null");
        try {
            Document document = mapper.toDocument(metadata, clock.instant(), extraction);
            writer.updateDocument(new Term(LuceneIndexSchema.PATH_KEY, metadata.normalizedPath()), document);
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not update the local search index.", exception);
        }
    }

    public void delete(Path path) {
        ensureOpen();
        try {
            writer.deleteDocuments(new Term(LuceneIndexSchema.PATH_KEY, PathNormalizer.searchKey(path)));
        } catch (IOException exception) {
            throw new IndexAccessException(
                    "DeepFind could not delete an entry from the local search index.", exception);
        }
    }

    public void deleteTree(Path path) {
        ensureOpen();
        String normalizedPath = PathNormalizer.searchKey(path);
        String descendantPrefix =
                normalizedPath.endsWith(File.separator) ? normalizedPath : normalizedPath + File.separator;
        try {
            writer.deleteDocuments(
                    new TermQuery(new Term(LuceneIndexSchema.PATH_KEY, normalizedPath)),
                    new PrefixQuery(new Term(LuceneIndexSchema.PATH_KEY, descendantPrefix)));
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not delete a tree from the local search index.", exception);
        }
    }

    public MetadataIndexSnapshot openMetadataSnapshot() {
        ensureOpen();
        try {
            searcherManager.maybeRefreshBlocking();
            return new LuceneMetadataSnapshot(searcherManager.acquire());
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not open an index metadata snapshot.", exception);
        }
    }

    public long deleteProvenMissingUnderRoot(Path root, ExclusionPolicy exclusions) {
        ensureOpen();
        Path absoluteRoot = PathNormalizer.absolute(root);
        Objects.requireNonNull(exclusions, "exclusions must not be null");
        String rootKey = PathNormalizer.searchKey(absoluteRoot);
        String descendantPrefix = rootKey.endsWith(File.separator) ? rootKey : rootKey + File.separator;
        Query scope = pathScope(rootKey, descendantPrefix);
        long deleted = 0;
        try {
            searcherManager.maybeRefreshBlocking();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                ScoreDoc after = null;
                while (true) {
                    TopDocs page = searcher.searchAfter(after, scope, 512);
                    if (page.scoreDocs.length == 0) {
                        break;
                    }
                    for (ScoreDoc hit : page.scoreDocs) {
                        Document document = searcher.storedFields().document(hit.doc);
                        FileMetadata metadata = mapper.fromDocument(document);
                        if (exclusions.excludes(absoluteRoot, metadata.absolutePath())
                                || Files.notExists(metadata.absolutePath(), LinkOption.NOFOLLOW_LINKS)) {
                            writer.deleteDocuments(new Term(LuceneIndexSchema.PATH_KEY, metadata.normalizedPath()));
                            deleted++;
                        }
                    }
                    after = page.scoreDocs[page.scoreDocs.length - 1];
                }
            } finally {
                searcherManager.release(searcher);
            }
            return deleted;
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not reconcile missing index entries.", exception);
        }
    }

    private static Query pathScope(String rootKey, String descendantPrefix) {
        BooleanQuery.Builder scope = new BooleanQuery.Builder();
        scope.add(new TermQuery(new Term(LuceneIndexSchema.PATH_KEY, rootKey)), BooleanClause.Occur.SHOULD);
        scope.add(new PrefixQuery(new Term(LuceneIndexSchema.PATH_KEY, descendantPrefix)), BooleanClause.Occur.SHOULD);
        scope.setMinimumNumberShouldMatch(1);
        return scope.build();
    }

    public void commit() {
        ensureOpen();
        try {
            writer.commit();
            searcherManager.maybeRefreshBlocking();
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not commit the local search index.", exception);
        }
    }

    public long count() {
        ensureOpen();
        try {
            searcherManager.maybeRefreshBlocking();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                return searcher.getIndexReader().numDocs();
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not read the local search index.", exception);
        }
    }

    public List<MetadataSearchResult> search(String queryText, int limit) {
        return searchPage(queryText, limit).results();
    }

    public MetadataSearchPage searchPage(String queryText, int limit) {
        return searchPage(queryText, limit, MetadataSearchFilters.none());
    }

    public MetadataSearchPage searchPage(String queryText, int limit, MetadataSearchFilters filters) {
        ensureOpen();
        Objects.requireNonNull(filters, "filters must not be null");
        String query =
                Objects.requireNonNull(queryText, "queryText must not be null").trim();
        if (query.isEmpty()) {
            return new MetadataSearchPage(0, List.of());
        }
        if (limit < 1 || limit > MAX_RESULT_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_RESULT_LIMIT);
        }

        try {
            ParsedSearchQuery parsedQuery = ParsedSearchQuery.parse(query);
            if (parsedQuery.literalText().isEmpty()) {
                return new MetadataSearchPage(0, List.of());
            }
            Query luceneQuery = applyFilters(buildQuery(parsedQuery), filters);
            searcherManager.maybeRefreshBlocking();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                TopDocs hits = searcher.search(luceneQuery, limit);
                boolean fuzzyFallback = false;
                Optional<String> fuzzyTerm = parsedQuery.fuzzyFilenameTerm();
                if (hits.scoreDocs.length == 0 && fuzzyTerm.isPresent()) {
                    Query fuzzyQuery = applyFilters(buildFuzzyFilenameQuery(fuzzyTerm.orElseThrow()), filters);
                    hits = searcher.search(fuzzyQuery, limit);
                    fuzzyFallback = true;
                }
                return new MetadataSearchPage(
                        hits.totalHits == null ? hits.scoreDocs.length : hits.totalHits.value(),
                        mapResults(searcher, hits.scoreDocs, query, parsedQuery, fuzzyFallback));
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not search the local index.", exception);
        }
    }

    private static Query applyFilters(Query textQuery, MetadataSearchFilters filters) {
        BooleanQuery.Builder filtered = new BooleanQuery.Builder();
        filtered.add(textQuery, BooleanClause.Occur.MUST);
        if (filters.kind() != null) {
            filtered.add(
                    new TermQuery(
                            new Term(LuceneIndexSchema.KIND, filters.kind().name())),
                    BooleanClause.Occur.FILTER);
        }
        if (filters.extension() != null) {
            filtered.add(
                    new TermQuery(new Term(LuceneIndexSchema.EXTENSION, filters.extension())),
                    BooleanClause.Occur.FILTER);
        }
        if (filters.modifiedAfter() != null || filters.modifiedBefore() != null) {
            long minimum = filters.modifiedAfter() == null
                    ? Long.MIN_VALUE
                    : filters.modifiedAfter().toEpochMilli();
            long maximum = filters.modifiedBefore() == null
                    ? Long.MAX_VALUE
                    : filters.modifiedBefore().toEpochMilli();
            filtered.add(
                    LongPoint.newRangeQuery(LuceneIndexSchema.MODIFIED_AT, minimum, maximum),
                    BooleanClause.Occur.FILTER);
        }
        if (filters.minSizeBytes() != null || filters.maxSizeBytes() != null) {
            long minimum = filters.minSizeBytes() == null ? 0 : filters.minSizeBytes();
            long maximum = filters.maxSizeBytes() == null ? Long.MAX_VALUE : filters.maxSizeBytes();
            filtered.add(
                    LongPoint.newRangeQuery(LuceneIndexSchema.SIZE_BYTES, minimum, maximum),
                    BooleanClause.Occur.FILTER);
        }
        return filtered.build();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            IOUtils.close(searcherManager, writer, analyzer, directory);
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not close the local search index cleanly.", exception);
        }
    }

    private Query buildQuery(ParsedSearchQuery parsedQuery) {
        String normalizedQuery = parsedQuery.literalText().toLowerCase(Locale.ROOT);
        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[] {LuceneIndexSchema.FILENAME, LuceneIndexSchema.PATH_TEXT, LuceneIndexSchema.CONTENT},
                analyzer,
                Map.of(
                        LuceneIndexSchema.FILENAME,
                        4.0f,
                        LuceneIndexSchema.PATH_TEXT,
                        1.0f,
                        LuceneIndexSchema.CONTENT,
                        0.5f));
        parser.setDefaultOperator(QueryParser.Operator.AND);

        BooleanQuery.Builder query = new BooleanQuery.Builder();
        query.add(
                new BoostQuery(new TermQuery(new Term(LuceneIndexSchema.FILENAME_EXACT, normalizedQuery)), 12.0f),
                BooleanClause.Occur.SHOULD);
        query.add(
                new BoostQuery(new PrefixQuery(new Term(LuceneIndexSchema.FILENAME_EXACT, normalizedQuery)), 8.0f),
                BooleanClause.Occur.SHOULD);
        BooleanQuery.Builder required = new BooleanQuery.Builder();
        boolean hasRequiredClause = false;
        if (!parsedQuery.unquotedText().isEmpty()) {
            try {
                required.add(parser.parse(QueryParser.escape(parsedQuery.unquotedText())), BooleanClause.Occur.MUST);
                hasRequiredClause = true;
            } catch (ParseException ignored) {
                // Exact and prefix clauses preserve plain-text behavior for punctuation-only input.
            }
        }
        for (String phrase : parsedQuery.phrases()) {
            Query phraseQuery = phraseQuery(phrase);
            if (phraseQuery != null) {
                required.add(phraseQuery, BooleanClause.Occur.MUST);
                hasRequiredClause = true;
            }
        }
        if (hasRequiredClause) {
            query.add(required.build(), BooleanClause.Occur.SHOULD);
        }
        query.setMinimumNumberShouldMatch(1);
        return query.build();
    }

    private static Query buildFuzzyFilenameQuery(String term) {
        int maximumEdits = term.length() < 6 ? 1 : 2;
        return new FuzzyQuery(new Term(LuceneIndexSchema.FILENAME, term), maximumEdits, 1, 50, true);
    }

    private Query phraseQuery(String phrase) {
        QueryBuilder builder = new QueryBuilder(analyzer);
        BooleanQuery.Builder fields = new BooleanQuery.Builder();
        addPhraseField(fields, builder, LuceneIndexSchema.FILENAME, phrase, 6.0f);
        addPhraseField(fields, builder, LuceneIndexSchema.PATH_TEXT, phrase, 1.5f);
        addPhraseField(fields, builder, LuceneIndexSchema.CONTENT, phrase, 1.0f);
        BooleanQuery query = fields.build();
        return query.clauses().isEmpty() ? null : query;
    }

    private static void addPhraseField(
            BooleanQuery.Builder fields, QueryBuilder builder, String field, String phrase, float boost) {
        Query fieldQuery = builder.createPhraseQuery(field, phrase);
        if (fieldQuery != null) {
            fields.add(new BoostQuery(fieldQuery, boost), BooleanClause.Occur.SHOULD);
        }
    }

    private List<MetadataSearchResult> mapResults(
            IndexSearcher searcher,
            ScoreDoc[] hits,
            String queryText,
            ParsedSearchQuery parsedQuery,
            boolean fuzzyFallback)
            throws IOException {
        List<MetadataSearchResult> results = new java.util.ArrayList<>(hits.length);
        for (ScoreDoc hit : hits) {
            Document document = searcher.storedFields().document(hit.doc);
            FileMetadata metadata = mapper.fromDocument(document);
            MetadataMatchType matchType =
                    fuzzyFallback ? MetadataMatchType.FUZZY_FILENAME : matchType(metadata, document, parsedQuery);
            SearchSnippet snippet = matchType == MetadataMatchType.CONTENT
                            || matchType == MetadataMatchType.EXACT_PHRASE
                    ? ContentSnippetGenerator.generate(document.get(LuceneIndexSchema.SNIPPET_SOURCE), queryText)
                            .orElse(null)
                    : null;
            results.add(new MetadataSearchResult(metadata, matchType, snippet));
        }
        return List.copyOf(results);
    }

    private static MetadataMatchType matchType(
            FileMetadata metadata, Document document, ParsedSearchQuery parsedQuery) {
        String query = parsedQuery.literalText().toLowerCase(Locale.ROOT);
        String filename = metadata.filename().toLowerCase(Locale.ROOT);
        if (filename.equals(query)) {
            return MetadataMatchType.EXACT_FILENAME;
        }
        if (filename.startsWith(query)) {
            return MetadataMatchType.FILENAME_PREFIX;
        }
        boolean allTermsInFilename = List.of(query.split("\\s+")).stream().allMatch(filename::contains);
        if (allTermsInFilename) {
            return MetadataMatchType.FILENAME;
        }
        String path = metadata.absolutePath().toString().toLowerCase(Locale.ROOT);
        boolean allTermsInPath = List.of(query.split("\\s+")).stream().allMatch(path::contains);
        if (allTermsInPath) {
            return MetadataMatchType.PATH;
        }
        String content = document.get(LuceneIndexSchema.SNIPPET_SOURCE);
        if (content != null && parsedQuery.phrases().stream().anyMatch(phrase -> containsPhrase(content, phrase))) {
            return MetadataMatchType.EXACT_PHRASE;
        }
        return MetadataMatchType.CONTENT;
    }

    private static boolean containsPhrase(String content, String phrase) {
        String normalizedContent = content.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalizedContent.contains(phrase.toLowerCase(Locale.ROOT));
    }

    private static void validateSchema(Directory directory) throws IOException {
        if (!DirectoryReader.indexExists(directory)) {
            return;
        }
        List<IndexCommit> commits = DirectoryReader.listCommits(directory);
        String actualVersion = commits.getLast().getUserData().get(LuceneIndexSchema.VERSION_KEY);
        if (!LuceneIndexSchema.VERSION.equals(actualVersion)) {
            throw new IndexSchemaMismatchException(LuceneIndexSchema.VERSION, String.valueOf(actualVersion));
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Lucene metadata index is closed.");
        }
    }

    private final class LuceneMetadataSnapshot implements MetadataIndexSnapshot {

        private final IndexSearcher searcher;
        private final AtomicBoolean released = new AtomicBoolean();

        private LuceneMetadataSnapshot(IndexSearcher searcher) {
            this.searcher = searcher;
        }

        @Override
        public Optional<MetadataIndexEntry> find(Path path) {
            if (released.get()) {
                throw new IllegalStateException("Index metadata snapshot is closed.");
            }
            try {
                TopDocs hits = searcher.search(
                        new TermQuery(new Term(LuceneIndexSchema.PATH_KEY, PathNormalizer.searchKey(path))), 1);
                if (hits.scoreDocs.length == 0) {
                    return Optional.empty();
                }
                Document document = searcher.storedFields().document(hits.scoreDocs[0].doc);
                return Optional.of(new MetadataIndexEntry(
                        mapper.fromDocument(document),
                        !"NOT_ATTEMPTED".equals(document.get(LuceneIndexSchema.EXTRACTION_STATUS))));
            } catch (IOException exception) {
                throw new IndexAccessException("DeepFind could not read an index metadata snapshot.", exception);
            }
        }

        @Override
        public void close() {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            try {
                searcherManager.release(searcher);
            } catch (IOException exception) {
                throw new IndexAccessException("DeepFind could not close an index metadata snapshot.", exception);
            }
        }
    }
}
