package com.deepfind.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepfind.jobs.IndexingJobService;
import com.deepfind.jobs.IndexingJobState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeepFindApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IndexingJobService jobs;

    @TempDir
    Path root;

    @Test
    void indexesAndSearchesThroughTheHttpApi() throws Exception {
        Path project = Files.createDirectories(root.resolve("Archive/Client ABC/Thesis"));
        Path expectedFile = Files.writeString(project.resolve("final_submission.docx"), "metadata only");
        String requestJson = "{\"root\":\"" + jsonEscape(root.toString()) + "\"}";

        mockMvc.perform(post("/api/index/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        awaitCompleted(Duration.ofSeconds(5));

        mockMvc.perform(get("/api/index/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.entriesIndexed").value(5))
                .andExpect(jsonPath("$.failures").value(0));

        mockMvc.perform(get("/api/search").param("query", "thesis final").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("thesis final"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].path")
                        .value(expectedFile.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$.results[0].filename").value("final_submission.docx"))
                .andExpect(jsonPath("$.results[0].matchType").value("PATH"));
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
