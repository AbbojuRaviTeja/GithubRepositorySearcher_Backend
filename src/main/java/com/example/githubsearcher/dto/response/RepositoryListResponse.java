package com.example.githubsearcher.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RepositoryListResponse(
        List<RepositoryResponse> repositories,
        @Schema(example = "0", description = "Zero-based page index")
        int page,
        @Schema(example = "20")
        int size,
        @Schema(example = "1")
        long totalElements,
        @Schema(example = "1")
        int totalPages) {
}
