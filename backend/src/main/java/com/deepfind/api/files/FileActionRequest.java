package com.deepfind.api.files;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FileActionRequest(
        @NotBlank @Size(max = 32_767) String path) {}
