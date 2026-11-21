package com.example.githubsearcher.repository;

import java.time.OffsetDateTime;

public record UpsertCommand(
        Long githubId,
        String name,
        String fullName,
        String description,
        String owner,
        String language,
        Long stars,
        Long forks,
        OffsetDateTime lastUpdated,
        String htmlUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
