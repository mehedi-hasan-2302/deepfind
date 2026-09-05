package com.deepfind.api.search;

import com.deepfind.filesystem.FileSystemEntryKind;
import com.deepfind.search.MetadataSearchFilters;
import com.deepfind.search.MetadataSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final MetadataSearchService search;

    public SearchController(MetadataSearchService search) {
        this.search = search;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam @NotBlank @Size(max = 500) String query,
            @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int limit,
            @RequestParam(required = false) FileSystemEntryKind kind,
            @RequestParam(required = false) @Size(max = 32) @Pattern(regexp = "^\\.?[\\p{L}\\p{N}][\\p{L}\\p{N}+_-]*$") String extension,
            @RequestParam(required = false) Instant modifiedAfter,
            @RequestParam(required = false) Instant modifiedBefore,
            @RequestParam(required = false) @PositiveOrZero Long minSizeBytes,
            @RequestParam(required = false) @PositiveOrZero Long maxSizeBytes) {
        MetadataSearchFilters filters =
                new MetadataSearchFilters(kind, extension, modifiedAfter, modifiedBefore, minSizeBytes, maxSizeBytes);
        return SearchResponse.from(search.search(query, limit, filters));
    }
}
