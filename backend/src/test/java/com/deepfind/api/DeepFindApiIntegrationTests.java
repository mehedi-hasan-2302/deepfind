package com.deepfind.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        mockMvc.perform(post("/api/index/start")
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

        mockMvc.perform(post("/api/index/refresh"))
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
                .andExpect(jsonPath("$.results[0].path")
                        .value(expectedFile.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$.results[0].filename").value("final_submission.docx"))
                .andExpect(jsonPath("$.results[0].matchType").value("PATH"));

        mockMvc.perform(get("/api/search").param("query", "starling").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].path")
                        .value(contentFile.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$.results[0].matchType").value("CONTENT"))
                .andExpect(jsonPath("$.results[0].snippet.text").value(containsString("starling")))
                .andExpect(jsonPath("$.results[0].snippet.highlights[0].start").isNumber())
                .andExpect(jsonPath("$.results[0].snippet.highlights[0].end").isNumber());
    }

    @Test
    void returnsStableErrorsForInvalidRootsAndQueries() throws Exception {
        Path missing = root.resolve("missing");

        mockMvc.perform(post("/api/index/start")
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
    }

    @Test
    void delegatesValidatedFileActionsThroughTheHttpApi() throws Exception {
        Path file = Files.writeString(root.resolve("final report.txt"), "metadata only");
        String requestJson = "{\"path\":\"" + jsonEscape(file.toString()) + "\"}";

        mockMvc.perform(post("/api/files/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.action").value("OPENED"));
        mockMvc.perform(post("/api/files/reveal")
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

        mockMvc.perform(post("/api/files/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_ACTION_INVALID"))
                .andExpect(jsonPath("$.message").value("This file or folder no longer exists."))
                .andExpect(jsonPath("$.details").isMap());
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
}
