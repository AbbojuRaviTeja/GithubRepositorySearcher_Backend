package com.example.githubsearcher.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SearchResponse(
        @Schema(example = "Repositories fetched and saved successfully")
        String message,
        List<RepositoryResponse> repositories) {
}
