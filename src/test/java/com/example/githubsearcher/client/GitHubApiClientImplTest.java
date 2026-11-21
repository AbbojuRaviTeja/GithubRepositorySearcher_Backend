package com.example.githubsearcher.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.githubsearcher.config.GitHubApiProperties;
import com.example.githubsearcher.config.RestClientConfig;
import com.example.githubsearcher.dto.github.GitHubApiResponse;
import com.example.githubsearcher.exception.GitHubApiException;
import com.example.githubsearcher.exception.GitHubRateLimitException;
import com.example.githubsearcher.model.SortField;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubApiClientImplTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        GitHubApiProperties properties = properties("");
        builder = RestClientConfig.baseBuilder(properties);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void mapsSuccessfulSearch() {
        server.expect(requestTo(allOf(
                        containsString("/search/repositories"),
                        containsString("per_page=30"),
                        containsString("page=1"),
                        containsString("sort=stars"),
                        containsString("order=desc"),
                        containsString("spring"),
                        containsString("language"))))
                .andRespond(withSuccess(oneItemJson(), MediaType.APPLICATION_JSON));

        List<GitHubApiResponse.Item> items = client(30, 1, false).search("spring boot", "Java", SortField.STARS);

        assertThat(items).hasSize(1);
        GitHubApiResponse.Item item = items.get(0);
        assertThat(item.id()).isEqualTo(123456L);
        assertThat(item.name()).isEqualTo("spring-boot-example");
        assertThat(item.owner().login()).isEqualTo("user123");
        assertThat(item.stargazersCount()).isEqualTo(450L);
        assertThat(item.forksCount()).isEqualTo(120L);
        assertThat(item.updatedAt()).isEqualTo(OffsetDateTime.parse("2024-01-01T12:00:00Z"));
        server.verify();
    }

    @Test
    void emptyItemsIsSuccess() {
        server.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withSuccess("""
                        {"total_count":0,"incomplete_results":false,"items":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client(30, 1, false).search("missing", null, null)).isEmpty();
        server.verify();
    }

    @Test
    void forbiddenWithRateLimitHeadersThrowsRateLimit() {
        server.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header(HttpHeaders.RETRY_AFTER, "60"));

        assertThatThrownBy(() -> client(30, 1, false).search("spring", null, SortField.STARS))
                .isInstanceOf(GitHubRateLimitException.class)
                .extracting(ex -> ((GitHubRateLimitException) ex).getRetryAfter())
                .isEqualTo("60");
    }

    @Test
    void tooManyRequestsThrowsRateLimit() {
        server.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "12"));

        assertThatThrownBy(() -> client(30, 1, false).search("spring", null, null))
                .isInstanceOf(GitHubRateLimitException.class);
    }

    @Test
    void serverErrorThrowsGitHubApiException() {
        server.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("upstream"));

        assertThatThrownBy(() -> client(30, 1, false).search("spring", null, null))
                .isInstanceOf(GitHubApiException.class)
                .extracting(ex -> ((GitHubApiException) ex).getStatus())
                .isEqualTo(502);
    }

    @Test
    void unprocessableQueryMapsTo400() {
        server.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("{\"message\":\"Validation Failed\"}"));

        assertThatThrownBy(() -> client(30, 1, false).search(":::", null, null))
                .isInstanceOf(GitHubApiException.class)
                .extracting(ex -> ((GitHubApiException) ex).getStatus())
                .isEqualTo(400);
    }

    @Test
    void invalidJsonThrowsGitHubApiException() {
        server.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(30, 1, false).search("spring", null, null))
                .isInstanceOf(GitHubApiException.class)
                .extracting(ex -> ((GitHubApiException) ex).getStatus())
                .isEqualTo(502);
    }

    @Test
    void fetchAllPagesStopsAtConfiguredCap() {
        server.expect(requestTo(containsString("page=1")))
                .andRespond(withSuccess(pageJson(1L, 2), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("page=2")))
                .andRespond(withSuccess(pageJson(2L, 2), MediaType.APPLICATION_JSON));

        List<GitHubApiResponse.Item> items = client(1, 2, true).search("spring", null, SortField.FORKS);

        assertThat(items).extracting(GitHubApiResponse.Item::id).containsExactly(1L, 2L);
        server.verify();
    }

    @Test
    void defaultDoesNotRequestSecondPage() {
        server.expect(requestTo(containsString("page=1")))
                .andRespond(withSuccess(pageJson(1L, 50), MediaType.APPLICATION_JSON));

        assertThat(client(1, 3, false).search("spring", null, null)).hasSize(1);
        server.verify();
    }

    @Test
    void sendsRequiredHeadersAndBearerToken() {
        GitHubApiProperties properties = properties("test-token");
        RestClient.Builder tokenBuilder = RestClientConfig.baseBuilder(properties);
        MockRestServiceServer tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
        tokenServer.expect(requestTo(containsString("/search/repositories")))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andExpect(header(HttpHeaders.USER_AGENT, "github-repository-searcher"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"total_count":0,"incomplete_results":false,"items":[]}
                        """, MediaType.APPLICATION_JSON));

        new GitHubApiClientImpl(tokenBuilder.build(), 30, 1, false).search("q", null, null);
        tokenServer.verify();
    }

    @Test
    void omitsAuthorizationWhenTokenBlank() {
        server.expect(requestTo(containsString("/search/repositories")))
                .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse())
                .andRespond(withSuccess("""
                        {"total_count":0,"incomplete_results":false,"items":[]}
                        """, MediaType.APPLICATION_JSON));

        client(30, 1, false).search("q", null, null);
        server.verify();
    }

    @Test
    void buildQueryAppendsLanguage() {
        assertThat(GitHubApiClientImpl.buildQuery("spring boot", "Java")).isEqualTo("spring boot language:Java");
        assertThat(GitHubApiClientImpl.buildQuery("spring", "  ")).isEqualTo("spring");
    }

    private GitHubApiClientImpl client(int perPage, int maxPages, boolean fetchAllPages) {
        return new GitHubApiClientImpl(builder.build(), perPage, maxPages, fetchAllPages);
    }

    private static GitHubApiProperties properties(String token) {
        GitHubApiProperties properties = new GitHubApiProperties();
        properties.setBaseUrl("https://api.github.com");
        properties.setToken(token);
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(10));
        properties.setPerPage(30);
        properties.setMaxPages(1);
        properties.setFetchAllPages(false);
        return properties;
    }

    private static String oneItemJson() {
        return """
                {
                  "total_count": 1,
                  "incomplete_results": false,
                  "items": [
                    {
                      "id": 123456,
                      "name": "spring-boot-example",
                      "full_name": "user123/spring-boot-example",
                      "description": "An example repository for Spring Boot",
                      "owner": {"login": "user123"},
                      "language": "Java",
                      "stargazers_count": 450,
                      "forks_count": 120,
                      "updated_at": "2024-01-01T12:00:00Z",
                      "html_url": "https://github.com/user123/spring-boot-example"
                    }
                  ]
                }
                """;
    }

    private static String pageJson(long id, long total) {
        return """
                {
                  "total_count": %d,
                  "incomplete_results": false,
                  "items": [
                    {
                      "id": %d,
                      "name": "repo-%d",
                      "full_name": "owner/repo-%d",
                      "stargazers_count": 1,
                      "forks_count": 1
                    }
                  ]
                }
                """.formatted(total, id, id, id);
    }
}
