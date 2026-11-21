package com.example.githubsearcher.repository;

import com.example.githubsearcher.entity.RepositoryEntity;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class RepositorySpecifications {

    private RepositorySpecifications() {
    }

    public static Specification<RepositoryEntity> matching(String language, Long minStars) {
        return matching(language, minStars, null, null, null);
    }

    public static Specification<RepositoryEntity> matching(
            String language,
            Long minStars,
            Long minForks,
            OffsetDateTime updatedFrom,
            OffsetDateTime updatedToExclusive) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (language != null && !language.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("language")),
                        language.trim().toLowerCase(Locale.ROOT)));
            }
            if (minStars != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("stars"), minStars));
            }
            if (minForks != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("forks"), minForks));
            }
            if (updatedFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("lastUpdated"), updatedFrom));
            }
            if (updatedToExclusive != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("lastUpdated"), updatedToExclusive));
            }
            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
