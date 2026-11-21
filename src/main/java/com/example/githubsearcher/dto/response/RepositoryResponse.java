package com.example.githubsearcher.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record RepositoryResponse(
        @Schema(example = "123456", description = "GitHub repository id, not the internal database primary key")
        Long id,
        @Schema(example = "spring-boot-example")
        String name,
        @Schema(example = "An example repository for Spring Boot")
        String description,
        @Schema(example = "user123", description = "GitHub owner login")
        String owner,
        @Schema(example = "Java")
        String language,
        @Schema(example = "450")
        Long stars,
        @Schema(example = "120")
        Long forks,
        @Schema(example = "2024-01-01T12:00:00Z")
        OffsetDateTime lastUpdated) {
}
