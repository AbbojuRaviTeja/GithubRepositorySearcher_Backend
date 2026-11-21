package com.example.githubsearcher.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.githubsearcher.client.GitHubApiClient;
import com.example.githubsearcher.dto.github.GitHubApiResponse;
import com.example.githubsearcher.dto.request.SearchRequest;
import com.example.githubsearcher.dto.response.RepositoryListResponse;
import com.example.githubsearcher.dto.response.SearchResponse;
import com.example.githubsearcher.entity.RepositoryEntity;
import com.example.githubsearcher.exception.GitHubApiException;
import com.example.githubsearcher.exception.GitHubRateLimitException;
import com.example.githubsearcher.exception.InvalidRequestException;
import com.example.githubsearcher.exception.ResourceNotFoundException;
import com.example.githubsearcher.mapper.RepositoryMapper;
import com.example.githubsearcher.model.SortField;
import com.example.githubsearcher.repository.GitHubRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class GitHubServiceImplTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private GitHubRepository gitHubRepository;

    private GitHubServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GitHubServiceImpl(gitHubApiClient, gitHubRepository, new RepositoryMapper());
    }

    @Test
    void searchPersistsEachRepository() {
        GitHubApiResponse.Item item = item(123456L, 450L);
        when(gitHubApiClient.search("spring boot", "Java", SortField.STARS)).thenReturn(List.of(item));

        SearchResponse response = service.search(new SearchRequest("  spring boot  ", " Java ", "Stars"));

        assertThat(response.message()).isEqualTo("Repositories fetched and saved successfully");
        assertThat(response.repositories()).hasSize(1);
        assertThat(response.repositories().get(0).id()).isEqualTo(123456L);
        verify(gitHubRepository).upsert(
                eq(123456L),
                eq("spring-boot-example"),
                eq("user123/spring-boot-example"),
                eq("An example repository for Spring Boot"),
                eq("user123"),
                eq("Java"),
                eq(450L),
                eq(120L),
                eq(OffsetDateTime.parse("2024-01-01T12:00:00Z")),
                eq("https://github.com/user123/spring-boot-example"),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class));
        verify(gitHubRepository, never()).save(any());
    }

    @Test
    void existingRepositoryIsUpdatedThroughUpsert() {
        when(gitHubApiClient.search(eq("spring"), isNull(), isNull())).thenReturn(List.of(item(7L, 10L)));

        service.search(new SearchRequest("spring", null, null));

        verify(gitHubRepository, times(1)).upsert(
                eq(7L), any(), any(), any(), any(), any(), eq(10L), any(), any(), any(), any(), any());
        verify(gitHubRepository, never()).save(any());
    }

    @Test
    void duplicateGithubIdIsUpsertedOnce() {
        GitHubApiResponse.Item first = item(42L, 1L);
        GitHubApiResponse.Item second = new GitHubApiResponse.Item(
                42L, "renamed", "user123/renamed", "newer", new GitHubApiResponse.Owner("user123"),
                "Java", 99L, 3L, OffsetDateTime.parse("2024-02-01T00:00:00Z"), "https://github.com/user123/renamed");
        when(gitHubApiClient.search(any(), any(), any())).thenReturn(List.of(first, second));

        SearchResponse response = service.search(new SearchRequest("spring", null, "stars"));

        assertThat(response.repositories()).hasSize(1);
        assertThat(response.repositories().get(0).name()).isEqualTo("renamed");
        assertThat(response.repositories().get(0).stars()).isEqualTo(99L);
        verify(gitHubRepository, times(1)).upsert(eq(42L), eq("renamed"), any(), any(), any(), any(), eq(99L), eq(3L), any(), any(), any(), any());
    }

    @Test
    void emptyGitHubResponseDoesNotPersist() {
        when(gitHubApiClient.search(any(), any(), any())).thenReturn(List.of());

        SearchResponse response = service.search(new SearchRequest("nothing", null, null));

        assertThat(response.message()).isEqualTo("No repositories found for the given search criteria");
        assertThat(response.repositories()).isEmpty();
        verify(gitHubRepository, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nullStarsAndForksAreStoredAsZero() {
        GitHubApiResponse.Item item = new GitHubApiResponse.Item(
                5L, "bare", null, null, null, null, null, null, null, null);
        when(gitHubApiClient.search(any(), any(), any())).thenReturn(List.of(item));

        SearchResponse response = service.search(new SearchRequest("bare", " ", null));

        assertThat(response.repositories().get(0).stars()).isZero();
        assertThat(response.repositories().get(0).forks()).isZero();
        assertThat(response.repositories().get(0).owner()).isNull();
        verify(gitHubRepository).upsert(eq(5L), eq("bare"), isNull(), isNull(), isNull(), isNull(), eq(0L), eq(0L), isNull(), isNull(), any(), any());
    }

    @Test
    void gitHubFailurePropagatesWithoutPersistence() {
        when(gitHubApiClient.search(any(), any(), any())).thenThrow(new GitHubApiException("down", 502));

        assertThatThrownBy(() -> service.search(new SearchRequest("spring", null, null)))
                .isInstanceOf(GitHubApiException.class)
                .hasMessage("down");
        verify(gitHubRepository, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rateLimitPropagates() {
        when(gitHubApiClient.search(any(), any(), any()))
                .thenThrow(new GitHubRateLimitException("GitHub API rate limit exceeded. Please retry later.", "15"));

        assertThatThrownBy(() -> service.search(new SearchRequest("spring", null, null)))
                .isInstanceOf(GitHubRateLimitException.class);
    }

    @Test
    void retrievalDelegatesFilterAndSort() {
        RepositoryEntity entity = new RepositoryEntity();
        entity.setGithubId(5L);
        entity.setName("demo");
        entity.setStars(40L);
        entity.setForks(2L);
        when(gitHubRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        RepositoryListResponse response = service.findRepositories(" Java ", 10L, "forks", 0, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(gitHubRepository).findAll(any(Specification.class), pageable.capture());
        Sort sort = pageable.getValue().getSort();
        assertThat(sort.getOrderFor("forks").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("githubId").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(response.repositories()).hasSize(1);
        assertThat(response.repositories().get(0).id()).isEqualTo(5L);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void omittedSortDefaultsToStarsDescending() {
        when(gitHubRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findRepositories(null, null, null, 0, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(gitHubRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("stars").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void negativeMinStarsRejected() {
        assertThatThrownBy(() -> service.findRepositories(null, -1L, "stars", 0, 20))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void unknownSortRejected() {
        assertThatThrownBy(() -> service.findRepositories(null, null, "name", 0, 20))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void missingRepositoryThrowsNotFound() {
        when(gitHubRepository.findByGithubId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByGithubId(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static GitHubApiResponse.Item item(long id, long stars) {
        return new GitHubApiResponse.Item(
                id,
                "spring-boot-example",
                "user123/spring-boot-example",
                "An example repository for Spring Boot",
                new GitHubApiResponse.Owner("user123"),
                "Java",
                stars,
                120L,
                OffsetDateTime.parse("2024-01-01T12:00:00Z"),
                "https://github.com/user123/spring-boot-example");
    }
}
