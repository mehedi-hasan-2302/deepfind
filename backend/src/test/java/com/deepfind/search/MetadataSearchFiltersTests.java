package com.deepfind.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MetadataSearchFiltersTests {

    @Test
    void normalizesOptionalExtensions() {
        MetadataSearchFilters filters = new MetadataSearchFilters(null, " .PDF ", null, null, null, null);

        assertThat(filters.extension()).isEqualTo("pdf");
        assertThat(MetadataSearchFilters.none().extension()).isNull();
    }

    @Test
    void rejectsReversedDateAndSizeRanges() {
        assertThatThrownBy(() -> new MetadataSearchFilters(
                        null,
                        null,
                        Instant.parse("2026-09-02T00:00:00Z"),
                        Instant.parse("2026-09-01T00:00:00Z"),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modifiedAfter");
        assertThatThrownBy(() -> new MetadataSearchFilters(null, null, null, null, 10L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minSizeBytes");
    }
}
