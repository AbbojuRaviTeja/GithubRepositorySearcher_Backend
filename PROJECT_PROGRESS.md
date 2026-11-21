# Project progress

Assignment source: `Backend.md`. Maven project is at the workspace root (`pom.xml`).

## Requirements checklist

- [x] Java 21, Spring Boot 3.4.13, Maven, Web, Data JPA, Validation, RestClient, springdoc 2.8.14
- [x] Package layout requested in the brief
- [x] `RepositoryEntity` fields, unique `github_id`, timestamps
- [x] Native `INSERT ... ON CONFLICT (github_id) DO UPDATE` plus `findByGithubId`
- [x] Flyway `V1__create_repositories.sql` and `ddl-auto=validate`
- [x] POST `/api/github/search` and GET `/api/github/repositories` (`id` = GitHub id)
- [x] GET by GitHub id for a real 404 (extra, documented)
- [x] Validation, rate limit, upstream, and database error mapping
- [x] Configurable timeouts, per-page, max pages, optional token
- [x] Controller, service, and client tests that do not call live GitHub
- [x] Testcontainers repository and integration tests (executed against PostgreSQL 16)
- [x] Dockerfile, docker-compose, `.env.example`, `.gitignore`
- [x] README with the 17 requested sections
- [x] `mvn -B test` and `mvn -B -DskipTests package`

## Phase status

1. Inspect workspace and scaffold Maven project — done. Workspace had only `Backend.md`; project created at the root.
2. Database layer — done.
3. GitHub client, DTOs, errors, client tests — done.
4. Service, mapper, upsert orchestration, filter/sort — done.
5. Controller, validation, exception handler — done.
6. Tests — done and passing, including Testcontainers.
7. Swagger, Docker, env example, gitignore, README — done.
8. Maven test and package — done.

## Environment notes

- `java` and `mvn` were not on PATH. Tests and the package build used a portable Temurin 21.0.13 JDK and Maven 3.9.9 under `%TEMP%\gh-searcher-tools` (not part of the project).
- Android Studio's bundled runtime is Java 25 and was not used.
- Docker Desktop was not running at the start. It was started so Testcontainers could pull `postgres:16-alpine`. Both PostgreSQL test classes ran; none were skipped.
- Local PostgreSQL 17 under `C:\Program Files\PostgreSQL\17` was not used by the test suite.
- No real GitHub token is stored in the repo. `.env.example` has an empty `GITHUB_TOKEN`.

## Test and build results

Command: `mvn -B test`  
Result: **PASS**  
Finished: 2026-11-20T10:52:44+05:30  
Total time: 2:30 min  

```
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
```

| Class | Tests | Result |
| --- | --- | --- |
| GitHubApiClientImplTest | 12 | pass |
| GitHubControllerTest | 12 | pass |
| GitHubSearchIntegrationTest | 2 | pass (Testcontainers PostgreSQL + mocked GitHub client) |
| GitHubRepositoryTest | 3 | pass (Testcontainers PostgreSQL, including ON CONFLICT upsert) |
| GitHubServiceImplTest | 12 | pass |

Notable: Surefire logged that it killed the forked JVM about 30 seconds after `System.exit(0)` during Testcontainers shutdown. The build still exited successfully. Failures: 0. Skipped: 0.

Command: `mvn -B -DskipTests package`  
Result: **PASS**  
Finished: 2026-11-20T10:53:13+05:30  
Total time: 13.3 s  
Artifact: `target/github-searcher-1.0.0.jar` (Spring Boot repackaged).

## Remaining tasks

None for the assignment. Optional follow-ups that were not required:

- Add the Maven Wrapper (`mvnw`) into the repo so a machine without a global Maven install can build without a portable download.
- `docker compose up --build` was not executed in this session (the image build was not run; unit/integration tests and `package` were).
