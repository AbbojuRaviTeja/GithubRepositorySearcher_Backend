package com.example.githubsearcher.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubApiResponse(
        @JsonProperty("total_count") long totalCount,
        @JsonProperty("incomplete_results") boolean incompleteResults,
        List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            Long id,
            String name,
            @JsonProperty("full_name") String fullName,
            String description,
            Owner owner,
            String language,
            @JsonProperty("stargazers_count") Long stargazersCount,
            @JsonProperty("forks_count") Long forksCount,
            @JsonProperty("updated_at") OffsetDateTime updatedAt,
            @JsonProperty("html_url") String htmlUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {
    }
}
