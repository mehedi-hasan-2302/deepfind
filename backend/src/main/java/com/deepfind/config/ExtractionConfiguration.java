package com.deepfind.config;

import com.deepfind.extraction.BoundedContentExtractor;
import com.deepfind.extraction.ContentExtractor;
import com.deepfind.extraction.DocumentParser;
import com.deepfind.extraction.TikaDocumentParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepFindExtractionProperties.class)
public class ExtractionConfiguration {

    @Bean
    DocumentParser documentParser() {
        return new TikaDocumentParser();
    }

    @Bean(destroyMethod = "close")
    ContentExtractor contentExtractor(DocumentParser parser, DeepFindExtractionProperties properties) {
        return new BoundedContentExtractor(parser, properties);
    }
}
