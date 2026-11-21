package com.example.githubsearcher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.githubsearcher.client.GitHubApiClient;
import com.example.githubsearcher.dto.github.GitHubApiResponse;
import com.example.githubsearcher.support.DockerAvailabilityCondition;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(DockerAvailabilityCondition.class)
class GitHubSearchIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubApiClient gitHubApiClient;

    @Test
    void searchPersistsAndGetReturnsStoredRepository() throws Exception {
        when(gitHubApiClient.search(any(), any(), any())).thenReturn(List.of(item(123456L, 450L, "Java")));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"spring boot","language":"Java","sort":"stars"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Repositories fetched and saved successfully"))
                .andExpect(jsonPath("$.repositories[0].id").value(123456))
                .andExpect(jsonPath("$.repositories[0].stars").value(450));

        mockMvc.perform(get("/api/github/repositories")
                        .param("language", "java")
                        .param("minStars", "100")
                        .param("sort", "stars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories[0].id").value(123456))
                .andExpect(jsonPath("$.repositories[0].name").value("spring-boot-example"))
                .andExpect(jsonPath("$.repositories[0].owner").value("user123"))
                .andExpect(jsonPath("$.totalElements").value(1));

        when(gitHubApiClient.search(any(), isNull(), any())).thenReturn(List.of(item(123456L, 900L, "Java")));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"spring boot","sort":"stars"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories[0].stars").value(900));

        mockMvc.perform(get("/api/github/repositories").param("language", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.repositories[0].stars").value(900));

        mockMvc.perform(get("/api/github/repositories/123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(123456))
                .andExpect(jsonPath("$.forks").value(120));
    }

    @Test
    void emptySearchIsSuccessAndDoesNotMatchOtherLanguages() throws Exception {
        when(gitHubApiClient.search(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"no-such-repo-xyz"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No repositories found for the given search criteria"))
                .andExpect(jsonPath("$.repositories").isEmpty());
    }

    private static GitHubApiResponse.Item item(long id, long stars, String language) {
        return new GitHubApiResponse.Item(
                id,
                "spring-boot-example",
                "user123/spring-boot-example",
                "An example repository for Spring Boot",
                new GitHubApiResponse.Owner("user123"),
                language,
                stars,
                120L,
                OffsetDateTime.parse("2024-01-01T12:00:00Z"),
                "https://github.com/user123/spring-boot-example");
    }
}
