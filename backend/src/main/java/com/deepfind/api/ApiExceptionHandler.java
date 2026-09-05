package com.deepfind.api;

import com.deepfind.index.IndexAccessException;
import com.deepfind.jobs.IndexRootNotAccessibleException;
import com.deepfind.jobs.IndexingAlreadyRunningException;
import com.deepfind.jobs.NoIndexRootSelectedException;
import com.deepfind.platform.FileActionUnavailableException;
import com.deepfind.platform.InvalidFileActionException;
import jakarta.validation.ConstraintViolationException;
import java.nio.file.InvalidPathException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IndexRootNotAccessibleException.class)
    ResponseEntity<ApiError> inaccessibleRoot(IndexRootNotAccessibleException exception) {
        return error(HttpStatus.BAD_REQUEST, "INDEX_ROOT_NOT_ACCESSIBLE", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IndexingAlreadyRunningException.class)
    ResponseEntity<ApiError> indexingAlreadyRunning(IndexingAlreadyRunningException exception) {
        return error(HttpStatus.CONFLICT, "INDEX_JOB_ALREADY_RUNNING", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(NoIndexRootSelectedException.class)
    ResponseEntity<ApiError> noIndexRootSelected(NoIndexRootSelectedException exception) {
        return error(HttpStatus.CONFLICT, "INDEX_ROOT_NOT_SELECTED", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IndexAccessException.class)
    ResponseEntity<ApiError> unavailableIndex() {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INDEX_UNAVAILABLE",
                "DeepFind's local search index is unavailable.",
                Map.of());
    }

    @ExceptionHandler(InvalidFileActionException.class)
    ResponseEntity<ApiError> invalidFileAction(InvalidFileActionException exception) {
        return error(HttpStatus.BAD_REQUEST, "FILE_ACTION_INVALID", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(FileActionUnavailableException.class)
    ResponseEntity<ApiError> unavailableFileAction(FileActionUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "FILE_ACTION_UNAVAILABLE", exception.getMessage(), Map.of());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HandlerMethodValidationException.class
    })
    ResponseEntity<ApiError> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request contains invalid values.", Map.of());
    }

    @ExceptionHandler({InvalidPathException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> invalidValue() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request contains an invalid value.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedFailure() {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "DeepFind could not complete the request.",
                Map.of());
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status, String code, String message, Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ApiError(code, message, details));
    }
}
