package com.example.githubsearcher.mapper;

import com.example.githubsearcher.dto.github.GitHubApiResponse;
import com.example.githubsearcher.dto.response.RepositoryResponse;
import com.example.githubsearcher.entity.RepositoryEntity;
import org.springframework.stereotype.Component;

@Component
public class RepositoryMapper {

    public RepositoryResponse toResponse(GitHubApiResponse.Item item) {
        return new RepositoryResponse(
                item.id(),
                item.name(),
                item.description(),
                item.owner() == null ? null : item.owner().login(),
                item.language(),
                zero(item.stargazersCount()),
                zero(item.forksCount()),
                item.updatedAt());
    }

    public RepositoryResponse toResponse(RepositoryEntity entity) {
        return new RepositoryResponse(
                entity.getGithubId(),
                entity.getName(),
                entity.getDescription(),
                entity.getOwner(),
                entity.getLanguage(),
                zero(entity.getStars()),
                zero(entity.getForks()),
                entity.getLastUpdated());
    }

    public static long zero(Long value) {
        return value == null ? 0L : value;
    }
}
