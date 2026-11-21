package com.example.githubsearcher.service;

import com.example.githubsearcher.client.GitHubApiClient;
import com.example.githubsearcher.dto.github.GitHubApiResponse;
import com.example.githubsearcher.dto.request.SearchRequest;
import com.example.githubsearcher.dto.response.RepositoryListResponse;
import com.example.githubsearcher.dto.response.RepositoryResponse;
import com.example.githubsearcher.dto.response.SearchResponse;
import com.example.githubsearcher.entity.RepositoryEntity;
import com.example.githubsearcher.exception.InvalidRequestException;
import com.example.githubsearcher.exception.ResourceNotFoundException;
import com.example.githubsearcher.mapper.RepositoryMapper;
import com.example.githubsearcher.model.SortField;
import com.example.githubsearcher.repository.GitHubRepository;
import com.example.githubsearcher.repository.RepositorySpecifications;
import com.example.githubsearcher.repository.UpsertCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubServiceImpl implements GitHubService {

    static final String SUCCESS_MESSAGE = "Repositories fetched and saved successfully";
    static final String EMPTY_MESSAGE = "No repositories found for the given search criteria";
    static final int MAX_PAGE_SIZE = 100;

    private final GitHubApiClient gitHubApiClient;
    private final GitHubRepository gitHubRepository;
    private final RepositoryMapper repositoryMapper;

    public GitHubServiceImpl(
            GitHubApiClient gitHubApiClient,
            GitHubRepository gitHubRepository,
            RepositoryMapper repositoryMapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubRepository = gitHubRepository;
        this.repositoryMapper = repositoryMapper;
    }

    @Override
    @Transactional
    public SearchResponse search(SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new InvalidRequestException("query must not be blank");
        }
        SortField sortField = SortField.parseOptional(request.sort());
        List<GitHubApiResponse.Item> items = gitHubApiClient.search(
                request.query().trim(),
                blankToNull(request.language()),
                sortField);
        if (items == null) {
            items = List.of();
        }

        Map<Long, GitHubApiResponse.Item> unique = new LinkedHashMap<>();
        for (GitHubApiResponse.Item item : items) {
            if (item == null || item.id() == null || item.name() == null || item.name().isBlank()) {
                continue;
            }
            unique.put(item.id(), item);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UpsertCommand> commands = new ArrayList<>();
        List<RepositoryResponse> responses = new ArrayList<>();
        for (GitHubApiResponse.Item item : unique.values()) {
            commands.add(new UpsertCommand(
                    item.id(),
                    item.name(),
                    item.fullName(),
                    item.description(),
                    item.owner() == null ? null : item.owner().login(),
                    item.language(),
                    RepositoryMapper.zero(item.stargazersCount()),
                    RepositoryMapper.zero(item.forksCount()),
                    item.updatedAt(),
                    item.htmlUrl(),
                    now,
                    now));
            responses.add(repositoryMapper.toResponse(item));
        }
        if (!commands.isEmpty()) {
            gitHubRepository.upsertAll(commands);
        }

        if (responses.isEmpty()) {
            return new SearchResponse(EMPTY_MESSAGE, List.of());
        }
        return new SearchResponse(SUCCESS_MESSAGE, List.copyOf(responses));
    }

    @Override
    @Transactional(readOnly = true)
    public RepositoryListResponse findRepositories(
            String language,
            Long minStars,
            Long minForks,
            OffsetDateTime updatedFrom,
            OffsetDateTime updatedToExclusive,
            String sort,
            int page,
            int size) {
        if (minStars != null && minStars < 0) {
            throw new InvalidRequestException("minStars must be greater than or equal to 0");
        }
        if (minForks != null && minForks < 0) {
            throw new InvalidRequestException("minForks must be greater than or equal to 0");
        }
        if (updatedFrom != null && updatedToExclusive != null && !updatedFrom.isBefore(updatedToExclusive)) {
            throw new InvalidRequestException("updatedFrom must be on or before updatedTo");
        }
        if (page < 0) {
            throw new InvalidRequestException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and 100");
        }
        SortField sortField = SortField.parseOrDefaultStars(sort);
        Pageable pageable = PageRequest.of(page, size, sortField.toSort());
        Page<RepositoryEntity> result = gitHubRepository.findAll(
                RepositorySpecifications.matching(
                        blankToNull(language), minStars, minForks, updatedFrom, updatedToExclusive),
                pageable);
        List<RepositoryResponse> repositories = result.getContent().stream()
                .map(repositoryMapper::toResponse)
                .toList();
        return new RepositoryListResponse(
                repositories,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public RepositoryResponse getByGithubId(Long githubId) {
        if (githubId == null || githubId <= 0) {
            throw new InvalidRequestException("githubId must be a positive number");
        }
        return gitHubRepository.findByGithubId(githubId)
                .map(repositoryMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found for id " + githubId));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
