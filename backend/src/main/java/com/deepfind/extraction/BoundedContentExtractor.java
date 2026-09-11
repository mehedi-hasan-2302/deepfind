package com.deepfind.extraction;

import com.deepfind.config.DeepFindExtractionProperties;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class BoundedContentExtractor implements ContentExtractor, AutoCloseable {

    private final DocumentParser parser;
    private final SupportedFilePolicy policy = new SupportedFilePolicy();
    private final long maxFileSizeBytes;
    private final int maxExtractedCharacters;
    private final Duration timeout;
    private final ThreadPoolExecutor executor;

    public BoundedContentExtractor(DocumentParser parser, DeepFindExtractionProperties properties) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        maxFileSizeBytes = properties.maxFileSizeBytes();
        maxExtractedCharacters = properties.maxExtractedCharacters();
        timeout = properties.timeout();
        executor = new ThreadPoolExecutor(
                properties.workerCount(),
                properties.workerCount(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                daemonThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public ExtractionResult extract(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        if (!policy.supportsExtension(path)) {
            return ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, "", "EXTENSION_NOT_SUPPORTED");
        }
        ExtractionResult rejectedInput = rejectUnsafeInput(path);
        if (rejectedInput != null) {
            return rejectedInput;
        }

        Future<ExtractionResult> extraction;
        try {
            extraction = executor.submit(() -> extractSupported(path));
        } catch (RejectedExecutionException exception) {
            String reason = executor.isShutdown() ? "EXTRACTOR_SHUTDOWN" : "EXTRACTION_CAPACITY_EXCEEDED";
            return ExtractionResult.outcome(ExtractionStatus.TIMEOUT, "", reason);
        }

        try {
            return extraction.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            cancelAndPurge(extraction);
            return ExtractionResult.outcome(ExtractionStatus.TIMEOUT, "", "EXTRACTION_DEADLINE_EXCEEDED");
        } catch (InterruptedException exception) {
            cancelAndPurge(extraction);
            Thread.currentThread().interrupt();
            return ExtractionResult.outcome(ExtractionStatus.TIMEOUT, "", "EXTRACTION_INTERRUPTED");
        } catch (CancellationException exception) {
            return ExtractionResult.outcome(ExtractionStatus.TIMEOUT, "", "EXTRACTOR_SHUTDOWN");
        } catch (ExecutionException exception) {
            return ExtractionResult.outcome(ExtractionStatus.PARSE_ERROR, "", "UNEXPECTED_PARSER_FAILURE");
        }
    }

    private ExtractionResult extractSupported(Path path) {
        ExtractionResult rejectedInput = rejectUnsafeInput(path);
        if (rejectedInput != null) {
            return rejectedInput;
        }
        String mediaType = "";
        try {
            mediaType = parser.detectMediaType(path);
            if (!policy.supportsDetectedType(path, mediaType)) {
                return ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, mediaType, "MEDIA_TYPE_NOT_SUPPORTED");
            }
            ParsedDocument parsed = parser.parse(path, maxExtractedCharacters);
            if (!policy.supportsDetectedType(path, parsed.mediaType())) {
                return ExtractionResult.outcome(
                        ExtractionStatus.UNSUPPORTED, parsed.mediaType(), "PARSER_MEDIA_TYPE_MISMATCH");
            }
            return ExtractionResult.success(parsed);
        } catch (AccessDeniedException | SecurityException exception) {
            return ExtractionResult.outcome(ExtractionStatus.PERMISSION_DENIED, mediaType, "FILE_NOT_READABLE");
        } catch (IOException exception) {
            return ExtractionResult.outcome(ExtractionStatus.PARSE_ERROR, mediaType, "FILE_READ_FAILED");
        } catch (DocumentParsingException exception) {
            return ExtractionResult.outcome(ExtractionStatus.PARSE_ERROR, mediaType, "DOCUMENT_PARSE_FAILED");
        }
    }

    @Override
    public void close() {
        executor.shutdownNow().forEach(task -> {
            if (task instanceof Future<?> future) {
                future.cancel(false);
            }
        });
        executor.purge();
        try {
            long waitMillis = Math.max(1, Math.min(timeout.toMillis(), 1_000));
            executor.awaitTermination(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private ExtractionResult rejectUnsafeInput(Path path) {
        try {
            BasicFileAttributes attributes =
                    Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                return ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, "", "INPUT_NOT_REGULAR_FILE");
            }
            if (attributes.size() > maxFileSizeBytes) {
                return ExtractionResult.outcome(ExtractionStatus.SKIPPED_TOO_LARGE, "", "FILE_SIZE_LIMIT_EXCEEDED");
            }
            return null;
        } catch (AccessDeniedException | SecurityException exception) {
            return ExtractionResult.outcome(ExtractionStatus.PERMISSION_DENIED, "", "FILE_NOT_READABLE");
        } catch (IOException exception) {
            return ExtractionResult.outcome(ExtractionStatus.PARSE_ERROR, "", "FILE_UNAVAILABLE");
        }
    }

    private void cancelAndPurge(Future<ExtractionResult> extraction) {
        extraction.cancel(true);
        executor.purge();
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "deepfind-extractor-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
