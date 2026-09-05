package com.deepfind.persistence;

import java.time.Instant;
import java.util.Optional;

public interface ApplicationSettingsRepository {

    Optional<String> findValue(String key);

    void put(String key, String value, Instant updatedAt);
}
