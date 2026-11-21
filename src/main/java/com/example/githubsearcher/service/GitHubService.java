package com.example.githubsearcher.service;

import com.example.githubsearcher.dto.request.SearchRequest;
import com.example.githubsearcher.dto.response.RepositoryListResponse;
import com.example.githubsearcher.dto.response.RepositoryResponse;
import com.example.githubsearcher.dto.response.SearchResponse;

public interface GitHubService {

    SearchResponse search(SearchRequest request);

    RepositoryListResponse findRepositories(String language, Long minStars, String sort, int page, int size);

    RepositoryResponse getByGithubId(Long githubId);
}
