package com.deepfind.api.search;

import com.deepfind.search.MetadataSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
            @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int limit) {
        return SearchResponse.from(search.search(query, limit));
    }
}
