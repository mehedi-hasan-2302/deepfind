package com.deepfind.api.index;

import com.deepfind.jobs.IndexingJobService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final IndexingJobService jobs;

    public IndexController(IndexingJobService jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexStatusResponse start(@Valid @RequestBody StartIndexRequest request) {
        return IndexStatusResponse.from(jobs.start(Path.of(request.root())));
    }

    @GetMapping("/status")
    public IndexStatusResponse status() {
        return IndexStatusResponse.from(jobs.status());
    }
}
