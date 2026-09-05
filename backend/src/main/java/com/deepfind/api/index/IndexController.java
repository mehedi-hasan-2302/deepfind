package com.deepfind.api.index;

import com.deepfind.index.IndexWatchCoordinator;
import com.deepfind.jobs.IndexingJobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final IndexingJobService jobs;
    private final IndexWatchCoordinator watches;

    public IndexController(IndexingJobService jobs, IndexWatchCoordinator watches) {
        this.jobs = jobs;
        this.watches = watches;
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

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexStatusResponse refresh() {
        return IndexStatusResponse.from(jobs.reconcileSelectedRoot());
    }

    @PostMapping("/pause")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexStatusResponse pause() {
        return IndexStatusResponse.from(jobs.pause());
    }

    @PostMapping("/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexStatusResponse resume() {
        return IndexStatusResponse.from(jobs.resume());
    }

    @GetMapping("/watch-status")
    public IndexWatchStatusResponse watchStatus() {
        return IndexWatchStatusResponse.from(watches.status());
    }

    @GetMapping("/history")
    public List<IndexStatusResponse> history(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return jobs.history(limit).stream().map(IndexStatusResponse::from).toList();
    }
}
