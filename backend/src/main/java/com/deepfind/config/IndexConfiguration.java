package com.deepfind.config;

import com.deepfind.index.IndexAccessException;
import com.deepfind.index.LuceneMetadataIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepFindStorageProperties.class)
public class IndexConfiguration {

    @Bean(destroyMethod = "close")
    LuceneMetadataIndex luceneMetadataIndex(DeepFindStorageProperties storage) {
        Path indexPath =
                storage.dataDirectory().resolve("index").toAbsolutePath().normalize();
        try {
            Files.createDirectories(indexPath);
        } catch (IOException exception) {
            throw new IndexAccessException("DeepFind could not create its local index directory.", exception);
        }
        return new LuceneMetadataIndex(indexPath);
    }
}
