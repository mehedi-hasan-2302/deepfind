package com.deepfind.config;

import com.deepfind.persistence.PersistenceAccessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

@Configuration
@EnableConfigurationProperties(DeepFindStorageProperties.class)
public class PersistenceConfiguration {

    @Bean
    DataSource dataSource(DeepFindStorageProperties storage) {
        Path dataDirectory = storage.dataDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException exception) {
            throw new PersistenceAccessException("DeepFind could not create its local data directory.", exception);
        }

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5_000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + dataDirectory.resolve("deepfind.db"));
        return dataSource;
    }
}
