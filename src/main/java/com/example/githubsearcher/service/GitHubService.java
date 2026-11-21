package com.example.githubsearcher.service;

import com.example.githubsearcher.dto.request.SearchRequest;
import com.example.githubsearcher.dto.response.RepositoryListResponse;
import com.example.githubsearcher.dto.response.RepositoryResponse;
import com.example.githubsearcher.dto.response.SearchResponse;
import java.time.OffsetDateTime;

public interface GitHubService {

    SearchResponse search(SearchRequest request);

    RepositoryListResponse findRepositories(
            String language,
            Long minStars,
            Long minForks,
            OffsetDateTime updatedFrom,
            OffsetDateTime updatedToExclusive,
            String sort,
            int page,
            int size);

    RepositoryResponse getByGithubId(Long githubId);
}
