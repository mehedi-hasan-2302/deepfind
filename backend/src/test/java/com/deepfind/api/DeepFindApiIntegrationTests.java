package com.deepfind.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepfind.jobs.IndexingJobService;
import com.deepfind.jobs.IndexingJobState;
import com.deepfind.platform.FileActions;
import com.deepfind.platform.InvalidFileActionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class DeepFindApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IndexingJobService jobs;

    @MockitoBean
    private FileActions fileActions;

    @TempDir
    Path root;

    @Test
    void indexesAndSearchesThroughTheHttpApi() throws Exception {
        Path project = Files.createDirectories(root.resolve("Archive/Client ABC/Thesis"));
        Path expectedFile = Files.writeString(project.resolve("final_submission.docx"), "metadata only");
        Path contentFile = Files.writeString(project.resolve("private-notes.txt"), "the internal codename is starling");
        String requestJson = "{\"root\":\"" + jsonEscape(root.toString()) + "\"}";

        mockMvc.perform(localPost("/api/index/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        awaitCompleted(Duration.ofSeconds(15));

        mockMvc.perform(get("/api/index/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.entriesIndexed").value(6))
                .andExpect(jsonPath("$.failures").value(0));

        mockMvc.perform(get("/api/index/watch-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root")
                        .value(root.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$.state").isString())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/index/history").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$[0].root")
                        .value(root.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$[0].entriesIndexed").value(6));

        mockMvc.perform(localPost("/api/index/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.root")
                        .value(root.toAbsolutePath().normalize().toString()));

        awaitCompleted(Duration.ofSeconds(15));

        mockMvc.perform(get("/api/index/history").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$[0].root")
                        .value(root.toAbsolutePath().normalize().toString()));

        mockMvc.perform(get("/api/search").param("query", "thesis final").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("thesis final"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.totalHitsExact").value(true))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.results[0].path")
                        .value(expectedFile.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$.results[0].filename").value("final_submission.docx"))
                .andExpect(jsonPath("$.results[0].matchType").value("PATH"));

        mockMvc.perform(get("/api/search")
                        .param("query", "final")
                        .param("kind", "FILE")
                        .param("extension", ".DOCX")
                        .param("minSizeBytes", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].filename").value("final_submission.docx"));

        mockMvc.perform(get("/api/search").param("query", "submision").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].filename").value("final_submission.docx"))
                .andExpect(jsonPath("$.results[0].matchType").value("FUZZY_FILENAME"))
                .andExpect(jsonPath("$.results[0].snippet").isEmpty());

        mockMvc.perform(get("/api/search")
                        .param("query", "archive")
                        .param("offset", "1")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(5))
                .andExpect(jsonPath("$.totalHitsExact").value(true))
                .andExpect(jsonPath("$.offset").value(1))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.results.length()").value(2));

        mockMvc.perform(get("/api/search").param("query", "starling").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].path")
                        .value(contentFile.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$.results[0].matchType").value("CONTENT"))
                .andExpect(jsonPath("$.results[0].snippet.text").value(containsString("starling")))
                .andExpect(jsonPath("$.results[0].snippet.highlights[0].start").isNumber())
                .andExpect(jsonPath("$.results[0].snippet.highlights[0].end").isNumber());

        mockMvc.perform(get("/api/search")
                        .param("query", "\"internal codename\"")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].matchType").value("EXACT_PHRASE"))
                .andExpect(jsonPath("$.results[0].snippet.text").value(containsString("internal codename")));
    }

    @Test
    void returnsStableErrorsForInvalidRootsAndQueries() throws Exception {
        Path missing = root.resolve("missing");

        mockMvc.perform(localPost("/api/index/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"root\":\"" + jsonEscape(missing.toString()) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INDEX_ROOT_NOT_ACCESSIBLE"))
                .andExpect(jsonPath("$.message").value("DeepFind cannot read this folder."))
                .andExpect(jsonPath("$.details").isMap());

        mockMvc.perform(get("/api/search").param("query", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/index/history").param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/search").param("query", "invoice").param("kind", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/search").param("query", "invoice").param("offset", "10001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(localPost("/api/index/pause"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INDEX_JOB_STATE_CONFLICT"));

        mockMvc.perform(localPost("/api/index/resume"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INDEX_JOB_STATE_CONFLICT"));

        mockMvc.perform(get("/api/search")
                        .param("query", "invoice")
                        .param("minSizeBytes", "100")
                        .param("maxSizeBytes", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void persistsFolderExclusionsAndReconcilesExistingResults() throws Exception {
        Path privateFolder = Files.createDirectories(root.resolve("Private"));
        Files.writeString(privateFolder.resolve("secret.txt"), "classifiedtoken");
        Files.writeString(root.resolve("visible.txt"), "publictoken");

        mockMvc.perform(localPost("/api/index/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"root\":\"" + jsonEscape(root.toString()) + "\"}"))
                .andExpect(status().isAccepted());
        awaitCompleted(Duration.ofSeconds(15));

        mockMvc.perform(get("/api/search").param("query", "classifiedtoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1));

        mockMvc.perform(localPut("/api/index/exclusions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[\"Private\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paths[0]").value("Private"))
                .andExpect(jsonPath("$.reconciliation.state").value("RUNNING"));
        awaitCompleted(Duration.ofSeconds(15));

        mockMvc.perform(get("/api/index/exclusions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root")
                        .value(root.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$.paths[0]").value("Private"));
        mockMvc.perform(get("/api/search").param("query", "classifiedtoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0));
        mockMvc.perform(get("/api/search").param("query", "publictoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1));

        mockMvc.perform(localPut("/api/index/exclusions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[\"../outside\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INDEX_EXCLUSION_INVALID"));
    }

    @Test
    void delegatesValidatedFileActionsThroughTheHttpApi() throws Exception {
        Path file = Files.writeString(root.resolve("final report.txt"), "metadata only");
        String requestJson = "{\"path\":\"" + jsonEscape(file.toString()) + "\"}";

        mockMvc.perform(localPost("/api/files/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.action").value("OPENED"));
        mockMvc.perform(localPost("/api/files/reveal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.action").value("REVEALED"));

        verify(fileActions).open(file);
        verify(fileActions).reveal(file);
    }

    @Test
    void returnsAStableErrorWhenAFileActionIsRejected() throws Exception {
        Path missing = root.resolve("missing.txt");
        String requestJson = "{\"path\":\"" + jsonEscape(missing.toString()) + "\"}";
        doThrow(new InvalidFileActionException("This file or folder no longer exists."))
                .when(fileActions)
                .open(missing);

        mockMvc.perform(localPost("/api/files/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_ACTION_INVALID"))
                .andExpect(jsonPath("$.message").value("This file or folder no longer exists."))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void rejectsCrossSiteAndUnguardedLocalApiRequests() throws Exception {
        mockMvc.perform(get("/api/health").header("Host", "attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_API_REQUEST_REJECTED"));

        mockMvc.perform(get("/api/health").header("Origin", "https://attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_API_REQUEST_REJECTED"));

        mockMvc.perform(get("/api/health").header("Referer", "https://attacker.example/page"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_API_REQUEST_REJECTED"));

        mockMvc.perform(post("/api/index/pause"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_API_REQUEST_REJECTED"));

        mockMvc.perform(localPost("/api/index/pause").header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_API_REQUEST_REJECTED"));

        mockMvc.perform(get("/api/health").header("Origin", "http://127.0.0.1:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    private void awaitCompleted(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (jobs.status().state() == IndexingJobState.RUNNING && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        if (jobs.status().state() != IndexingJobState.COMPLETED) {
            throw new AssertionError(
                    "Indexing job did not complete: " + jobs.status().state());
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static MockHttpServletRequestBuilder localPost(String path) {
        return post(path).header(LocalApiRequestFilter.CLIENT_HEADER, LocalApiRequestFilter.CLIENT_HEADER_VALUE);
    }

    private static MockHttpServletRequestBuilder localPut(String path) {
        return put(path).header(LocalApiRequestFilter.CLIENT_HEADER, LocalApiRequestFilter.CLIENT_HEADER_VALUE);
    }
}
