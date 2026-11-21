package com.example.githubsearcher.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.githubsearcher.entity.RepositoryEntity;
import com.example.githubsearcher.model.SortField;
import com.example.githubsearcher.support.DockerAvailabilityCondition;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(DockerAvailabilityCondition.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class GitHubRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private GitHubRepository repository;

    @Test
    void saveAndFindByGithubId() {
        RepositoryEntity saved = repository.saveAndFlush(entity(10L, "Java", 5L, 1L, timestamp("2024-01-01T00:00:00Z")));

        assertThat(saved.getId()).isNotNull();
        RepositoryEntity found = repository.findByGithubId(10L).orElseThrow();
        assertThat(found.getName()).isEqualTo("repo-10");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(repository.findByGithubId(999L)).isEmpty();
    }

    @Test
    void upsertInsertsThenUpdatesWithoutDuplicating() {
        OffsetDateTime created = timestamp("2024-01-01T00:00:00Z");
        OffsetDateTime updated = timestamp("2024-06-01T00:00:00Z");
        OffsetDateTime firstSeen = timestamp("2023-01-01T00:00:00Z");
        OffsetDateTime secondSeen = timestamp("2024-08-01T00:00:00Z");

        int inserted = repository.upsert(77L, "old", "o/old", "first", "owner", "Java", 1L, 2L,
                firstSeen, "https://github.com/o/old", created, created);
        int updatedRows = repository.upsert(77L, "new", "o/new", "second", "owner2", "Kotlin", 50L, 9L,
                secondSeen, "https://github.com/o/new", updated, updated);

        assertThat(inserted).isEqualTo(1);
        assertThat(updatedRows).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);

        RepositoryEntity found = repository.findByGithubId(77L).orElseThrow();
        assertThat(found.getName()).isEqualTo("new");
        assertThat(found.getLanguage()).isEqualTo("Kotlin");
        assertThat(found.getStars()).isEqualTo(50L);
        assertThat(found.getForks()).isEqualTo(9L);
        assertThat(found.getOwner()).isEqualTo("owner2");
        assertThat(found.getCreatedAt().toInstant()).isEqualTo(created.toInstant());
        assertThat(found.getUpdatedAt().toInstant()).isEqualTo(updated.toInstant());
        assertThat(found.getLastUpdated().toInstant()).isEqualTo(secondSeen.toInstant());
    }

    @Test
    void filtersLanguageCaseInsensitivelyAndMinStarsAndSorts() {
        repository.saveAll(List.of(
                entity(1L, "Java", 10L, 8L, timestamp("2024-01-01T00:00:00Z")),
                entity(2L, "Java", 50L, 1L, timestamp("2024-03-01T00:00:00Z")),
                entity(3L, "Python", 500L, 20L, timestamp("2024-04-01T00:00:00Z"))));
        repository.flush();

        Page<RepositoryEntity> byStars = repository.findAll(
                RepositorySpecifications.matching("java", 20L),
                PageRequest.of(0, 20, SortField.STARS.toSort()));
        assertThat(byStars.getContent()).extracting(RepositoryEntity::getGithubId).containsExactly(2L);

        Page<RepositoryEntity> byForks = repository.findAll(
                RepositorySpecifications.matching("JAVA", null),
                PageRequest.of(0, 20, SortField.FORKS.toSort()));
        assertThat(byForks.getContent()).extracting(RepositoryEntity::getGithubId).containsExactly(1L, 2L);

        Page<RepositoryEntity> byUpdated = repository.findAll(
                RepositorySpecifications.matching("java", null),
                PageRequest.of(0, 20, SortField.UPDATED.toSort()));
        assertThat(byUpdated.getContent()).extracting(RepositoryEntity::getGithubId).containsExactly(2L, 1L);
    }

    private static RepositoryEntity entity(long githubId, String language, long stars, long forks, OffsetDateTime updated) {
        RepositoryEntity entity = new RepositoryEntity();
        entity.setGithubId(githubId);
        entity.setName("repo-" + githubId);
        entity.setFullName("owner/repo-" + githubId);
        entity.setDescription("desc");
        entity.setOwner("owner");
        entity.setLanguage(language);
        entity.setStars(stars);
        entity.setForks(forks);
        entity.setLastUpdated(updated);
        entity.setHtmlUrl("https://github.com/owner/repo-" + githubId);
        return entity;
    }

    private static OffsetDateTime timestamp(String value) {
        return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
