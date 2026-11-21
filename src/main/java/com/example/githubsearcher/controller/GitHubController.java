package com.example.githubsearcher.controller;

import com.example.githubsearcher.dto.request.SearchRequest;
import com.example.githubsearcher.dto.response.ApiErrorResponse;
import com.example.githubsearcher.dto.response.RepositoryListResponse;
import com.example.githubsearcher.dto.response.RepositoryResponse;
import com.example.githubsearcher.dto.response.SearchResponse;
import com.example.githubsearcher.service.GitHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github")
@Validated
@Tag(name = "GitHub repositories", description = "Search GitHub and query repositories stored in PostgreSQL")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search GitHub repositories and save them",
            description = "Builds a GitHub search query, fetches the configured result page, upserts each repository, and returns the saved rows. The id field is the GitHub repository id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repositories fetched and saved, or no matches",
                    content = @Content(schema = @Schema(implementation = SearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "GitHub rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "GitHub API unavailable or rejected the request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Could not connect to the GitHub API",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return gitHubService.search(request);
    }

    @GetMapping(value = "/repositories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List repositories stored in PostgreSQL",
            description = "Does not call GitHub. Filters are optional. Default sort is stars descending, with github id descending as a tie-break. page defaults to 0 and size defaults to 20 (maximum 100).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching repositories, possibly empty",
                    content = @Content(schema = @Schema(implementation = RepositoryListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RepositoryListResponse list(
            @Parameter(description = "Case-insensitive programming language")
            @RequestParam(required = false) @Size(max = 64) String language,
            @Parameter(description = "Minimum star count, inclusive")
            @RequestParam(required = false) @Min(value = 0, message = "minStars must be greater than or equal to 0") Long minStars,
            @Parameter(description = "Minimum fork count, inclusive")
            @RequestParam(required = false) @Min(value = 0, message = "minForks must be greater than or equal to 0") Long minForks,
            @Parameter(description = "Include repositories updated on or after this date (UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate updatedFrom,
            @Parameter(description = "Include repositories updated on or before this date (UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate updatedTo,
            @Parameter(description = "stars, forks, or updated. Defaults to stars descending")
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)stars|forks|updated", message = "sort must be one of: stars, forks, updated") String sort,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @Parameter(description = "Page size, 1 to 100")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") int size) {
        return gitHubService.findRepositories(
                language,
                minStars,
                minForks,
                startOfDay(updatedFrom),
                startOfNextDay(updatedTo),
                sort,
                page,
                size);
    }

    private static OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime startOfNextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    @GetMapping(value = "/repositories/{githubId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get one stored repository by GitHub id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repository found",
                    content = @Content(schema = @Schema(implementation = RepositoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No stored repository with that GitHub id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RepositoryResponse getByGithubId(@PathVariable Long githubId) {
        return gitHubService.getByGithubId(githubId);
    }
}
