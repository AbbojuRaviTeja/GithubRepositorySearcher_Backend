package com.example.githubsearcher.repository;

import com.example.githubsearcher.entity.RepositoryEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GitHubRepository extends JpaRepository<RepositoryEntity, Long>, JpaSpecificationExecutor<RepositoryEntity>, GitHubRepositoryCustom {

    Optional<RepositoryEntity> findByGithubId(Long githubId);

    /**
     * Insert or update by GitHub repository id. PostgreSQL rejects a second insert for the same
     * {@code github_id} and applies the update instead, including under concurrent transactions.
     * {@code created_at} is written only on insert.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO repositories (
                github_id, name, full_name, description, owner, language,
                stars, forks, last_updated, html_url, created_at, updated_at
            ) VALUES (
                :githubId, :name, :fullName, :description, :owner, :language,
                :stars, :forks, :lastUpdated, :htmlUrl, :createdAt, :updatedAt
            )
            ON CONFLICT (github_id) DO UPDATE SET
                name = EXCLUDED.name,
                full_name = EXCLUDED.full_name,
                description = EXCLUDED.description,
                owner = EXCLUDED.owner,
                language = EXCLUDED.language,
                stars = EXCLUDED.stars,
                forks = EXCLUDED.forks,
                last_updated = EXCLUDED.last_updated,
                html_url = EXCLUDED.html_url,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsert(
            @Param("githubId") Long githubId,
            @Param("name") String name,
            @Param("fullName") String fullName,
            @Param("description") String description,
            @Param("owner") String owner,
            @Param("language") String language,
            @Param("stars") Long stars,
            @Param("forks") Long forks,
            @Param("lastUpdated") OffsetDateTime lastUpdated,
            @Param("htmlUrl") String htmlUrl,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("updatedAt") OffsetDateTime updatedAt);
}
