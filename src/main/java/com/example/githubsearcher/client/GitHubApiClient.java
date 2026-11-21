package com.example.githubsearcher.client;

import com.example.githubsearcher.dto.github.GitHubApiResponse;
import com.example.githubsearcher.model.SortField;
import java.util.List;

public interface GitHubApiClient {

    List<GitHubApiResponse.Item> search(String query, String language, SortField sort);
}
