package com.deepfind.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteApplicationSettingsRepository implements ApplicationSettingsRepository {

    private final JdbcClient jdbc;

    public SqliteApplicationSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findValue(String key) {
        return jdbc.sql("SELECT setting_value FROM application_settings WHERE setting_key = :key")
                .param("key", key)
                .query(String.class)
                .optional();
    }

    @Override
    public void put(String key, String value, Instant updatedAt) {
        jdbc.sql("""
                        INSERT INTO application_settings (setting_key, setting_value, updated_at)
                        VALUES (:key, :value, :updatedAt)
                        ON CONFLICT(setting_key) DO UPDATE SET
                            setting_value = excluded.setting_value,
                            updated_at = excluded.updated_at
                        """)
                .param("key", key)
                .param("value", value)
                .param("updatedAt", updatedAt.toString())
                .update();
    }
}
