package com.example.githubsearcher.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank(message = "query must not be blank")
        @Size(max = 256, message = "query must be at most 256 characters")
        @Schema(example = "spring boot", description = "Keyword matched against GitHub repository name and description")
        String query,

        @Size(max = 64, message = "language must be at most 64 characters")
        @Schema(example = "Java", description = "Optional language qualifier appended to the GitHub search query")
        String language,

        @Pattern(regexp = "(?i)stars|forks|updated", message = "sort must be one of: stars, forks, updated")
        @Schema(example = "stars", description = "Optional sort: stars, forks, or updated. Omit to use GitHub relevance.",
                allowableValues = {"stars", "forks", "updated"})
        String sort) {
}
