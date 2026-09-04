package com.deepfind.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deepfind.storage")
public record DeepFindStorageProperties(Path dataDirectory) {}
