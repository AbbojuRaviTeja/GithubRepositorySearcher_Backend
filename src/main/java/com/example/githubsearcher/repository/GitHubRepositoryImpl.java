package com.example.githubsearcher.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class GitHubRepositoryImpl implements GitHubRepositoryCustom {

    private static final String UPDATE = """
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
            """;

    private final EntityManager entityManager;

    public GitHubRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public int upsertAll(List<UpsertCommand> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder();
        sql.append("""
                INSERT INTO repositories (
                    github_id, name, full_name, description, owner, language,
                    stars, forks, last_updated, html_url, created_at, updated_at
                ) VALUES
                """);
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append("(?,?,?,?,?,?,?,?,?,?,?,?)");
        }
        sql.append('\n').append(UPDATE);
        Query query = entityManager.createNativeQuery(sql.toString());
        int index = 1;
        for (UpsertCommand row : rows) {
            query.setParameter(index++, row.githubId());
            query.setParameter(index++, row.name());
            query.setParameter(index++, row.fullName());
            query.setParameter(index++, row.description());
            query.setParameter(index++, row.owner());
            query.setParameter(index++, row.language());
            query.setParameter(index++, row.stars());
            query.setParameter(index++, row.forks());
            query.setParameter(index++, row.lastUpdated());
            query.setParameter(index++, row.htmlUrl());
            query.setParameter(index++, row.createdAt());
            query.setParameter(index++, row.updatedAt());
        }
        return query.executeUpdate();
    }
}
