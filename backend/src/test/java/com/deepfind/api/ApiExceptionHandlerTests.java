package com.deepfind.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.jobs.IndexingJobStateConflictException;
import com.deepfind.jobs.NoIndexRootSelectedException;
import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTests {

    @Test
    void mapsMissingRefreshRootToAStableConflict() {
        var response = new ApiExceptionHandler().noIndexRootSelected(new NoIndexRootSelectedException());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INDEX_ROOT_NOT_SELECTED");
        assertThat(response.getBody().message()).isEqualTo("Choose and index a folder before refreshing it.");
    }

    @Test
    void mapsInvalidPauseOrResumeTransitionsToAStableConflict() {
        var response = new ApiExceptionHandler()
                .indexingStateConflict(new IndexingJobStateConflictException("Only a paused job can resume."));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INDEX_JOB_STATE_CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Only a paused job can resume.");
    }
}
