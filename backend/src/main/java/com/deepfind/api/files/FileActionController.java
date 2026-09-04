package com.deepfind.api.files;

import com.deepfind.platform.FileActions;
import jakarta.validation.Valid;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileActionController {

    private final FileActions fileActions;

    public FileActionController(FileActions fileActions) {
        this.fileActions = fileActions;
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FileActionResponse open(@Valid @RequestBody FileActionRequest request) {
        fileActions.open(Path.of(request.path()));
        return new FileActionResponse("OPENED");
    }

    @PostMapping("/reveal")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FileActionResponse reveal(@Valid @RequestBody FileActionRequest request) {
        fileActions.reveal(Path.of(request.path()));
        return new FileActionResponse("REVEALED");
    }
}
