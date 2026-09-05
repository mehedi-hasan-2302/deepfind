package com.deepfind.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteIndexedRootRepository implements IndexedRootRepository {

    private final JdbcClient jdbc;

    public SqliteIndexedRootRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void rememberSelected(String pathKey, Path absolutePath, Instant selectedAt) {
        jdbc.sql("""
                        INSERT INTO indexed_roots (
                            path_key, absolute_path, enabled, created_at, last_selected_at, last_indexed_at
                        ) VALUES (:pathKey, :absolutePath, 1, :selectedAt, :selectedAt, NULL)
                        ON CONFLICT(path_key) DO UPDATE SET
                            absolute_path = excluded.absolute_path,
                            enabled = 1,
                            last_selected_at = excluded.last_selected_at
                        """)
                .param("pathKey", pathKey)
                .param("absolutePath", absolutePath.toString())
                .param("selectedAt", selectedAt.toString())
                .update();
    }

    @Override
    public void markIndexed(String pathKey, Instant indexedAt) {
        int updated = jdbc.sql("UPDATE indexed_roots SET last_indexed_at = :indexedAt WHERE path_key = :pathKey")
                .param("indexedAt", indexedAt.toString())
                .param("pathKey", pathKey)
                .update();
        if (updated != 1) {
            throw new PersistenceAccessException("DeepFind could not find the indexed root to update.");
        }
    }

    @Override
    public Optional<IndexedRootRecord> findByPathKey(String pathKey) {
        return jdbc.sql("""
                        SELECT path_key, absolute_path, enabled, created_at, last_selected_at, last_indexed_at
                        FROM indexed_roots
                        WHERE path_key = :pathKey
                        """)
                .param("pathKey", pathKey)
                .query((resultSet, rowNumber) -> new IndexedRootRecord(
                        resultSet.getString("path_key"),
                        Path.of(resultSet.getString("absolute_path")),
                        resultSet.getBoolean("enabled"),
                        Instant.parse(resultSet.getString("created_at")),
                        Instant.parse(resultSet.getString("last_selected_at")),
                        nullableInstant(resultSet.getString("last_indexed_at"))))
                .optional();
    }

    private static Instant nullableInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
