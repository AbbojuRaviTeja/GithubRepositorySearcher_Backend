package com.example.githubsearcher.repository;

import com.example.githubsearcher.entity.RepositoryEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class RepositorySpecifications {

    private RepositorySpecifications() {
    }

    public static Specification<RepositoryEntity> matching(String language, Long minStars) {
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
            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
