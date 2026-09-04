package com.deepfind.api.index;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartIndexRequest(
        @NotBlank @Size(max = 32_767) String root) {}
