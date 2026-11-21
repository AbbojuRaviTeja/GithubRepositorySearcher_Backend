package com.example.githubsearcher.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.githubsearcher.config.WebConfig;
import com.example.githubsearcher.dto.request.SearchRequest;
import com.example.githubsearcher.dto.response.RepositoryListResponse;
import com.example.githubsearcher.dto.response.RepositoryResponse;
import com.example.githubsearcher.dto.response.SearchResponse;
import com.example.githubsearcher.exception.GitHubApiException;
import com.example.githubsearcher.exception.GitHubRateLimitException;
import com.example.githubsearcher.exception.ResourceNotFoundException;
import com.example.githubsearcher.service.GitHubService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GitHubController.class)
@Import(WebConfig.class)
class GitHubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubService gitHubService;

    @Test
    void searchReturnsSavedRepositories() throws Exception {
        RepositoryResponse repository = sample(123456L);
        when(gitHubService.search(new SearchRequest("spring boot", "Java", "stars")))
                .thenReturn(new SearchResponse("Repositories fetched and saved successfully", List.of(repository)));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"spring boot","language":"Java","sort":"stars"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Repositories fetched and saved successfully"))
                .andExpect(jsonPath("$.repositories[0].id").value(123456))
                .andExpect(jsonPath("$.repositories[0].name").value("spring-boot-example"))
                .andExpect(jsonPath("$.repositories[0].owner").value("user123"))
                .andExpect(jsonPath("$.repositories[0].language").value("Java"))
                .andExpect(jsonPath("$.repositories[0].stars").value(450))
                .andExpect(jsonPath("$.repositories[0].forks").value(120))
                .andExpect(jsonPath("$.repositories[0].lastUpdated").value("2024-01-01T12:00:00Z"));
    }

    @Test
    void blankQueryReturns400() throws Exception {
        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/github/search"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("query"));

        verify(gitHubService, never()).search(any());
    }

    @Test
    void invalidSortReturns400() throws Exception {
        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"spring","sort":"name"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    @Test
    void vercelOriginIsAllowed() throws Exception {
        when(gitHubService.findRepositories(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new RepositoryListResponse(List.of(), 0, 20, 0, 0));

        String origin = "https://github-repository-searcher-frontend.vercel.app";
        mockMvc.perform(options("/api/github/repositories")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));

        mockMvc.perform(get("/api/github/repositories").header("Origin", origin))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    void retrieveRepositories() throws Exception {
        when(gitHubService.findRepositories(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new RepositoryListResponse(List.of(sample(123456L)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories[0].id").value(123456))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void retrieveAppliesLanguageMinStarsAndSort() throws Exception {
        when(gitHubService.findRepositories("Java", 100L, "forks", 0, 20))
                .thenReturn(new RepositoryListResponse(List.of(sample(123456L)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/github/repositories")
                        .param("language", "Java")
                        .param("minStars", "100")
                        .param("sort", "forks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories[0].stars").value(450));

        verify(gitHubService).findRepositories("Java", 100L, "forks", 0, 20);
    }

    @Test
    void invalidListSortReturns400() throws Exception {
        mockMvc.perform(get("/api/github/repositories").param("sort", "name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(gitHubService, never()).findRepositories(any(), any(), any(), eq(0), eq(20));
    }

    @Test
    void negativeMinStarsReturns400() throws Exception {
        mockMvc.perform(get("/api/github/repositories").param("minStars", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verify(gitHubService, never()).findRepositories(any(), any(), any(), eq(0), eq(20));
    }

    @Test
    void rateLimitReturns429AndRetryAfter() throws Exception {
        when(gitHubService.search(any())).thenThrow(
                new GitHubRateLimitException("GitHub API rate limit exceeded. Please retry later.", "30"));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"spring"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.message").value("GitHub API rate limit exceeded. Please retry later."));
    }

    @Test
    void gitHubFailureReturns502() throws Exception {
        when(gitHubService.search(any())).thenThrow(new GitHubApiException("GitHub API is unavailable", 502));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"spring"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("GitHub API is unavailable"));
    }

    @Test
    void databaseErrorDoesNotLeakSql() throws Exception {
        when(gitHubService.search(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"spring"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("A database error occurred"));
    }

    @Test
    void missingRepositoryReturns404() throws Exception {
        when(gitHubService.getByGithubId(9L)).thenThrow(new ResourceNotFoundException("Repository not found for id 9"));

        mockMvc.perform(get("/api/github/repositories/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Repository not found for id 9"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/github/repositories/abc"))
                .andExpect(status().isBadRequest());
    }

    private static RepositoryResponse sample(long githubId) {
        return new RepositoryResponse(
                githubId,
                "spring-boot-example",
                "An example repository for Spring Boot",
                "user123",
                "Java",
                450L,
                120L,
                OffsetDateTime.parse("2024-01-01T12:00:00Z"));
    }
}
