# GitHub Repository Searcher

Spring Boot service that searches public GitHub repositories, upserts them into PostgreSQL, and serves the stored rows through filterable REST endpoints. There is no UI. Exercise the API with Swagger, Postman, or curl.

## 1. Overview

`POST /api/github/search` calls the GitHub Search API, maps each repository, and upserts it by GitHub id. `GET /api/github/repositories` reads PostgreSQL only and filters by language, minimum stars, and sort. A lookup by GitHub id is available at `GET /api/github/repositories/{githubId}`.

The Maven project lives at the workspace root (`pom.xml` next to this file). Group `com.example`, artifact `github-searcher`, version `1.0.0`. Base package `com.example.githubsearcher`.

## 2. Problem statement

Users need a backend that can search GitHub by keyword, language, and sort, keep a local copy of the repositories that were returned, and query that copy without calling GitHub again. Re-running a search for a repository that is already stored must update the row instead of inserting a duplicate, including when two requests race.

## 3. Features

- Search GitHub repositories by keyword, optional language, and optional sort (`stars`, `forks`, `updated`).
- Persist name, description, owner login, language, stars, forks, last update, full name, and HTML URL.
- PostgreSQL `INSERT ... ON CONFLICT (github_id) DO UPDATE` so the GitHub id stays unique under concurrency.
- List stored repositories with case-insensitive language filter, `minStars`, and a fixed sort enum (no user input in `ORDER BY`).
- Optional page metadata (`page`, `size`, `totalElements`, `totalPages`) while always returning a `repositories` array.
- Lookup of one stored repository by GitHub id, with 404 when it is absent.
- Validation errors, GitHub rate limits, and upstream failures mapped to stable JSON error bodies.
- OpenAPI / Swagger UI.
- Docker Compose for PostgreSQL 16 and the app.
- Unit tests that never call the live GitHub API. PostgreSQL tests use Testcontainers and are skipped when Docker is down.

## 4. Tech stack

| Piece | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.4.13 |
| Build | Maven |
| HTTP API | Spring Web (servlet) |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL, Flyway |
| Validation | Jakarta Bean Validation |
| GitHub client | Spring `RestClient` (`JdkClientHttpRequestFactory` timeouts) |
| API docs | springdoc-openapi 2.8.14 |
| Tests | JUnit 5, Mockito, MockMvc, AssertJ, Testcontainers PostgreSQL 16 |
| Runtime packaging | Multi-stage Docker image (`eclipse-temurin` 21, non-root) |

Lombok is not used. WebClient and RestTemplate are not the GitHub client.

## 5. Architecture

```
HTTP request
    -> GitHubController          (validation, status codes, no business logic)
        -> GitHubServiceImpl     (query rules, dedupe, upsert orchestration, filters)
            -> GitHubApiClientImpl   (RestClient, GitHub headers, error mapping)
            -> GitHubRepository      (JPA + native upsert)
            -> RepositoryMapper      (entity/GitHub item -> response DTO)
    -> GlobalExceptionHandler    (ApiErrorResponse, no stack traces)
```

Controllers never return `RepositoryEntity`. The `id` field in JSON is the GitHub repository id (`githubId`), not the database primary key.

## 6. Directory structure

```
pom.xml
Dockerfile
docker-compose.yml
.env.example
README.md
PROJECT_PROGRESS.md
src/main/java/com/example/githubsearcher/
  GithubSearcherApplication.java
  client/          GitHubApiClient, GitHubApiClientImpl
  config/          RestClientConfig, OpenApiConfig, GitHubApiProperties
  controller/      GitHubController
  dto/github/      GitHubApiResponse
  dto/request/     SearchRequest
  dto/response/    SearchResponse, RepositoryResponse, RepositoryListResponse, ApiErrorResponse
  entity/          RepositoryEntity
  exception/       GlobalExceptionHandler and API exceptions
  mapper/          RepositoryMapper
  model/           SortField
  repository/      GitHubRepository, RepositorySpecifications
  service/         GitHubService, GitHubServiceImpl
src/main/resources/
  application.yml
  db/migration/V1__create_repositories.sql
src/test/java/com/example/githubsearcher/
  controller, service, client, repository, integration tests
```

## 7. Database schema

Table name: **`repositories`**.

| Column | Type | Notes |
| --- | --- | --- |
| id | BIGINT identity | Internal primary key. Not exposed in the API. |
| github_id | BIGINT NOT NULL | Unique (`uk_repositories_github_id`). This is the API `id`. |
| name | VARCHAR(255) NOT NULL | |
| full_name | VARCHAR(512) | Stored, not returned in the API DTO. |
| description | VARCHAR(4000) | Nullable. |
| owner | VARCHAR(255) | GitHub login. Nullable. |
| language | VARCHAR(255) | Nullable. |
| stars | BIGINT NOT NULL | Null from GitHub is stored as 0. |
| forks | BIGINT NOT NULL | Null from GitHub is stored as 0. |
| last_updated | TIMESTAMP(6) WITH TIME ZONE | Nullable. |
| html_url | VARCHAR(2048) | Stored, not returned in the API DTO. |
| created_at | TIMESTAMP(6) WITH TIME ZONE NOT NULL | Set on insert only. |
| updated_at | TIMESTAMP(6) WITH TIME ZONE NOT NULL | Set on insert and update. |

Indexes: unique `github_id`, `LOWER(language)`, `stars`, `forks`, `last_updated`.

Flyway migration `V1__create_repositories.sql` owns the schema. `spring.jpa.hibernate.ddl-auto=validate`. The JDBC URL does not create the database itself; PostgreSQL must already have a database named `github_searcher` (Docker Compose creates it from `POSTGRES_DB`).

Upsert:

```sql
INSERT INTO repositories (...)
VALUES (...)
ON CONFLICT (github_id) DO UPDATE SET
    name = EXCLUDED.name,
    ...
    updated_at = EXCLUDED.updated_at
```

`created_at` is not part of the `DO UPDATE` clause. `JpaRepository.save()` is not used for search results, because two concurrent `save()` calls can both insert before either sees the other.

## 8. Environment configuration

`.env` is gitignored and loaded automatically from the project root (`spring.config.import`). Do not commit `.env`, a database password, or a GitHub token. Nothing in the repo contains a real secret.

For Neon, open `.env` and set the host, database, role, and password from the Neon **Connect** dialog. Use the database password, not a Neon API key. `DB_SSL_MODE` must stay `require`. Neon must already contain the database named in `DB_NAME` (usually `neondb`); Flyway creates the tables on startup.

To use the Docker Postgres service instead, comment the Neon block in `.env` and uncomment the local block in `.env.example`.

| Variable | Default | Purpose |
| --- | --- | --- |
| DB_HOST | localhost | PostgreSQL host |
| DB_PORT | 5432 | PostgreSQL port |
| DB_NAME | github_searcher | Database name |
| DB_USERNAME | postgres | User |
| DB_PASSWORD | postgres | Password |
| DB_SSL_MODE | disable | JDBC `sslmode`. Set to `require` for Neon |
| GITHUB_TOKEN | empty | Sent as `Authorization: Bearer` only when non-blank |
| GITHUB_API_BASE_URL | https://api.github.com | Override in tests or proxies |
| GITHUB_CONNECT_TIMEOUT | 5s | JDK HTTP connect timeout |
| GITHUB_READ_TIMEOUT | 10s | RestClient read timeout |
| GITHUB_PER_PAGE | 30 | GitHub `per_page` (clamped to 1–100) |
| GITHUB_MAX_PAGES | 1 | Page cap when multi-page fetch is on (clamped to 1–10) |
| GITHUB_FETCH_ALL_PAGES | false | When true, follow pages up to `GITHUB_MAX_PAGES` |

Server port is `8080`.

Docker Compose reads `GITHUB_TOKEN` from the shell environment (`${GITHUB_TOKEN:-}`) and passes it to the app. It does not load a `.env` file unless you use a Compose version that does so automatically from the project directory; exporting the variable or using `--env-file .env` is explicit.

## 9. Installation

Requirements:

- JDK 21 (the compiler target is 21)
- Maven 3.9+
- PostgreSQL 16 (local) or Docker with Docker Compose
- Optional: a GitHub personal access token

```bash
git clone <your-repo-url>
cd Project
```

Create the database when you are not using Docker Compose:

```bash
psql -U postgres -c "CREATE DATABASE github_searcher;"
```

The application user must be allowed to create tables in that database (Flyway runs on startup).

Windows PowerShell example for the current shell only:

```powershell
$env:DB_PASSWORD = "postgres"
$env:GITHUB_TOKEN = ""   # or a real token; do not commit it
```

## 10. Run locally

From the directory that contains `pom.xml`:

```bash
mvn -B spring-boot:run
```

Or package and run the jar:

```bash
mvn -B -DskipTests package
java -jar target/github-searcher-1.0.0.jar
```

Hikari waits up to 60 seconds for the first database connection (`initialization-fail-timeout`), so a just-started local Postgres usually does not need a manual restart of the app.

Health of the process: the JVM stays up and logs Flyway migrating `V1__create_repositories.sql` on a fresh database. Open `http://localhost:8080/swagger-ui.html`.

## 11. Docker Compose

From the project root:

```bash
docker compose up --build
```

This starts:

- `postgres:16-alpine` with database, user, and password `github_searcher` / `postgres` / `postgres`, volume `pgdata`, and a `pg_isready` health check.
- The app image, built from the multi-stage `Dockerfile`, after Postgres is healthy. Port `8080` is published. `restart: on-failure` covers a late database if the health check ever races.

Pass a token without writing it into the compose file:

```bash
# bash
GITHUB_TOKEN=your_token_here docker compose up --build

# PowerShell
$env:GITHUB_TOKEN = "your_token_here"
docker compose up --build
```

Stop and keep data:

```bash
docker compose down
```

Stop and delete the database volume:

```bash
docker compose down -v
```

The image runs as a non-root `app` user and listens on 8080.

## 12. Tests

Tests do not call `api.github.com`. The HTTP client is exercised with `MockRestServiceServer`. Controller tests use MockMvc and a mocked service. Service tests use Mockito. Repository and full-stack tests use Testcontainers PostgreSQL 16.

```bash
mvn -B test
mvn -B -DskipTests package
```

When Docker is not running, `GitHubRepositoryTest` and `GitHubSearchIntegrationTest` are disabled (`@Testcontainers(disabledWithoutDocker = true)` plus a JUnit condition that treats a missing daemon as a skip). Unit tests still run. A green `mvn test` without Docker means the PostgreSQL tests were skipped, not that they passed against Postgres. Start Docker and rerun `mvn -B test` to execute them.

Latest command output is recorded in `PROJECT_PROGRESS.md`.

What is covered:

- Successful search JSON, blank query, invalid sort.
- List endpoint, `language` / `minStars` / `sort`, negative `minStars`, unknown sort.
- Service upsert once per GitHub id (duplicates in one payload collapse to one upsert), empty GitHub payload, API failure, rate limit, default sort `stars` descending with `githubId` tie-break.
- Client success mapping, empty `items`, HTTP 403 rate limit, HTTP 429, HTTP 500, HTTP 422, bad JSON, single-page vs capped multi-page, required headers, bearer token only when configured.
- Repository save, `findByGithubId`, upsert update without a second row, case-insensitive language, `minStars`, and sorts (when Docker is available).
- Integration: POST search persists through the real upsert, GET returns it, a second search updates stars in place (when Docker is available).

## 13. Swagger

With the app running:

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

`OpenApiConfig` sets the title **GitHub Repository Searcher API**, a short description, and version `1.0.0`. Endpoints declare `@Operation` and `@ApiResponses`.

## 14. Postman examples

Base URL: `http://localhost:8080`.

**Search**

- Method: `POST`
- URL: `http://localhost:8080/api/github/search`
- Header: `Content-Type: application/json`
- Body (raw JSON):

```json
{
  "query": "spring boot",
  "language": "Java",
  "sort": "stars"
}
```

`language` and `sort` may be omitted. `sort` must be `stars`, `forks`, or `updated` (any case) when present.

**List stored repositories**

- Method: `GET`
- URL: `http://localhost:8080/api/github/repositories?language=Java&minStars=100&sort=stars`

Other accepted query parameters: `page` (default `0`), `size` (default `20`, maximum `100`).

**One repository**

- Method: `GET`
- URL: `http://localhost:8080/api/github/repositories/123456`

curl:

```bash
curl -s -X POST http://localhost:8080/api/github/search \
  -H "Content-Type: application/json" \
  -d "{\"query\":\"spring boot\",\"language\":\"Java\",\"sort\":\"stars\"}"

curl -s "http://localhost:8080/api/github/repositories?language=Java&minStars=100&sort=stars"
```

PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/github/search `
  -ContentType "application/json" `
  -Body '{"query":"spring boot","language":"Java","sort":"stars"}'

Invoke-RestMethod -Uri "http://localhost:8080/api/github/repositories?language=Java&minStars=100&sort=stars"
```

## 15. Sample JSON

Search success:

```json
{
  "message": "Repositories fetched and saved successfully",
  "repositories": [
    {
      "id": 123456,
      "name": "spring-boot-example",
      "description": "An example repository for Spring Boot",
      "owner": "user123",
      "language": "Java",
      "stars": 450,
      "forks": 120,
      "lastUpdated": "2024-01-01T12:00:00Z"
    }
  ]
}
```

Search with no GitHub matches (HTTP 200):

```json
{
  "message": "No repositories found for the given search criteria",
  "repositories": []
}
```

List (HTTP 200). `repositories` matches the assignment. Page fields are extra and safe to ignore:

```json
{
  "repositories": [
    {
      "id": 123456,
      "name": "spring-boot-example",
      "description": "An example repository for Spring Boot",
      "owner": "user123",
      "language": "Java",
      "stars": 450,
      "forks": 120,
      "lastUpdated": "2024-01-01T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

An empty filter match is HTTP 200 with `"repositories": []` and zero totals, not a 404.

Error:

```json
{
  "timestamp": "2024-01-01T12:00:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/github/search",
  "fieldErrors": [
    { "field": "query", "message": "query must not be blank" }
  ]
}
```

`fieldErrors` is omitted when there are none. Responses never include stack traces, SQL, or the GitHub token.

## 16. Error handling

| Situation | HTTP | Notes |
| --- | --- | --- |
| Blank or missing `query`, invalid `sort`, negative `minStars`, bad page/size, malformed JSON, non-numeric id | 400 | Bean Validation or `InvalidRequestException` |
| GitHub search validation (HTTP 422 from GitHub) | 400 | Message: `Invalid GitHub search parameters` |
| Unknown stored GitHub id on `GET /api/github/repositories/{githubId}` | 404 | Empty search or empty list is not a 404 |
| GitHub 429, or 403 with rate-limit headers / rate-limit body | 429 | `Retry-After` is copied when GitHub sent a safe value |
| GitHub 5xx or other rejected GitHub responses | 502 | Generic message, upstream body is not returned |
| Connection failure or timeout | 503 | |
| Unparseable GitHub body | 502 | |
| Database errors | 500 | Message: `A database error occurred` |
| Any other unexpected error | 500 | Message: `An unexpected error occurred` |

Dates are ISO-8601 UTC. Null descriptions and languages are allowed. Star and fork counts that GitHub omits become `0`.

## 17. Design decisions and assumptions

- **Project location.** The workspace contained only `Backend.md`, so `pom.xml` is at the workspace root rather than in a `github-searcher/` subdirectory.
- **Pagination against GitHub.** The search request body has no page field. The default is one GitHub page (`per_page` from config, default 30, `order=desc`). `GITHUB_FETCH_ALL_PAGES=false` and `GITHUB_MAX_PAGES=1` keep a single user request from walking the whole result set (GitHub caps search at 1,000 hits). Set `GITHUB_FETCH_ALL_PAGES=true` to request further pages until a short page, `total_count`, or the page cap (hard max 10). When `sort` is omitted, the sort query parameter is omitted and GitHub uses relevance; `order=desc` is still sent. When `sort` is present it is mapped only through `SortField` (`stars`, `forks`, `updated`).
- **List pagination.** `GET /api/github/repositories` defaults to page `0`, size `20`, max size `100`. The body always contains `repositories` plus page metadata so clients that only read `repositories` keep working. Default sort is stars descending, then `githubId` descending. `minStars` means `stars >= minStars`. Language comparison is case-insensitive.
- **Response id.** `RepositoryResponse.id` is `github_id`. `full_name` and `html_url` are stored for later use and are not part of the assignment response.
- **Duplicates inside one GitHub page.** Items are deduped by GitHub id (last occurrence wins) and each id is upserted once.
- **Schema management.** Flyway plus `ddl-auto=validate` rather than Hibernate `update`, so production schema changes stay in SQL. Repository tests that use Testcontainers turn Flyway off and use `create-drop` so they do not depend on migration ordering inside the JPA slice.
- **Rate limits.** Anonymous GitHub search is about 60 requests per hour. A token is optional and is never hardcoded. 403 is a rate limit only when `Retry-After` is present, `X-RateLimit-Remaining` is `0`, or the body mentions a rate limit. Other 403s become 502 so a bad token is not echoed.
- **404.** Empty results are success. 404 is reserved for `GET /api/github/repositories/{githubId}` when that GitHub id is not stored. That route is an addition to the two assignment endpoints.
- **Timeouts.** Connect 5s and read 10s via the JDK HTTP client, both configurable.
- **Tests without Docker.** The suite must stay green when Docker is absent. PostgreSQL-specific `ON CONFLICT` is not executed on H2. Those tests skip instead of being rewritten in H2.
- **No security layer.** The API is unauthenticated, matching the assignment (Postman, no UI). Do not expose it to the public internet with a GitHub token configured without adding your own authentication.
- **Local database password.** Defaults match Docker Compose (`postgres` / `postgres`). A machine-local PostgreSQL install may use a different password; set `DB_PASSWORD`.

Build and test results from the last run in this workspace are in `PROJECT_PROGRESS.md`.
