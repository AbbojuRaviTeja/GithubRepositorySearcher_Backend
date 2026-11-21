package com.example.githubsearcher.client;

import com.example.githubsearcher.config.GitHubApiProperties;
import com.example.githubsearcher.dto.github.GitHubApiResponse;
import com.example.githubsearcher.exception.GitHubApiException;
import com.example.githubsearcher.exception.GitHubRateLimitException;
import com.example.githubsearcher.model.SortField;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GitHubApiClientImpl implements GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClientImpl.class);
    private static final int HARD_MAX_PER_PAGE = 100;
    private static final int HARD_MAX_PAGES = 10;

    private final RestClient restClient;
    private final int perPage;
    private final int maxPages;
    private final boolean fetchAllPages;

    public GitHubApiClientImpl(RestClient githubRestClient, GitHubApiProperties properties) {
        this(githubRestClient, properties.getPerPage(), properties.getMaxPages(), properties.isFetchAllPages());
    }

    public GitHubApiClientImpl(RestClient restClient, int perPage, int maxPages, boolean fetchAllPages) {
        this.restClient = restClient;
        this.perPage = Math.max(1, Math.min(perPage, HARD_MAX_PER_PAGE));
        this.maxPages = Math.max(1, Math.min(maxPages, HARD_MAX_PAGES));
        this.fetchAllPages = fetchAllPages;
    }

    @Override
    public List<GitHubApiResponse.Item> search(String query, String language, SortField sort) {
        String q = buildQuery(query, language);
        int pages = fetchAllPages ? maxPages : 1;
        List<GitHubApiResponse.Item> collected = new ArrayList<>();
        long totalCount = 0;
        for (int page = 1; page <= pages; page++) {
            GitHubApiResponse response = fetchPage(q, sort, page);
            List<GitHubApiResponse.Item> items = response == null || response.items() == null
                    ? List.of()
                    : response.items();
            totalCount = response == null ? 0 : response.totalCount();
            if (items.isEmpty()) {
                break;
            }
            collected.addAll(items);
            boolean reachedEnd = items.size() < perPage || collected.size() >= totalCount;
            if (!fetchAllPages || reachedEnd) {
                break;
            }
        }
        if (totalCount > collected.size()) {
            log.info("GitHub reported {} matches; returning {} from at most {} page(s)", totalCount, collected.size(), pages);
        }
        return List.copyOf(collected);
    }

    static String buildQuery(String query, String language) {
        StringBuilder builder = new StringBuilder(query == null ? "" : query.trim());
        if (language != null && !language.isBlank()) {
            builder.append(" language:").append(language.trim());
        }
        return builder.toString();
    }

    private GitHubApiResponse fetchPage(String q, SortField sort, int page) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/search/repositories")
                                .queryParam("q", q)
                                .queryParam("order", "desc")
                                .queryParam("per_page", perPage)
                                .queryParam("page", page);
                        if (sort != null) {
                            uriBuilder.queryParam("sort", sort.toGitHubSort());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.TOO_MANY_REQUESTS.value(), (request, response) -> {
                        throw new GitHubRateLimitException(
                                "GitHub API rate limit exceeded. Please retry later.",
                                sanitizeRetryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
                    })
                    .onStatus(status -> status.value() == HttpStatus.FORBIDDEN.value(), (request, response) -> {
                        String retryAfter = sanitizeRetryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
                        if (isRateLimited(response, retryAfter)) {
                            throw new GitHubRateLimitException(
                                    "GitHub API rate limit exceeded. Please retry later.",
                                    retryAfter);
                        }
                        throw new GitHubApiException("GitHub API rejected the request", HttpStatus.BAD_GATEWAY.value());
                    })
                    .onStatus(status -> status.value() == HttpStatus.UNPROCESSABLE_ENTITY.value(), (request, response) -> {
                        throw new GitHubApiException("Invalid GitHub search parameters", HttpStatus.BAD_REQUEST.value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new GitHubApiException("GitHub API is unavailable", HttpStatus.BAD_GATEWAY.value());
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new GitHubApiException("GitHub API request failed", HttpStatus.BAD_GATEWAY.value());
                    })
                    .body(GitHubApiResponse.class);
        } catch (GitHubRateLimitException | GitHubApiException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new GitHubApiException("Failed to communicate with the GitHub API", HttpStatus.SERVICE_UNAVAILABLE.value(), ex);
        } catch (RestClientException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof GitHubRateLimitException rateLimit) {
                throw rateLimit;
            }
            if (cause instanceof GitHubApiException api) {
                throw api;
            }
            throw new GitHubApiException("GitHub API returned an invalid response", HttpStatus.BAD_GATEWAY.value(), ex);
        }
    }

    private static boolean isRateLimited(ClientHttpResponse response, String retryAfter) throws IOException {
        if (retryAfter != null) {
            return true;
        }
        String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
        if (remaining != null && "0".equals(remaining.trim())) {
            return true;
        }
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return body.contains("rate limit") || body.contains("rate-limit");
    }

    private static String sanitizeRetryAfter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64 || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            return null;
        }
        return trimmed;
    }
}
