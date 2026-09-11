package com.deepfind.api.index;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateIndexExclusionsRequest(
        @NotNull @Size(max = 100) List<@Valid @NotBlank @Size(max = 500) String> paths) {

    public UpdateIndexExclusionsRequest {
        if (paths != null) {
            paths = List.copyOf(paths);
        }
    }
}
