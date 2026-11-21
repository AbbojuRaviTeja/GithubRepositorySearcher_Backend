package com.example.githubsearcher.model;

import com.example.githubsearcher.exception.InvalidRequestException;
import java.util.Locale;
import org.springframework.data.domain.Sort;

public enum SortField {
    STARS,
    FORKS,
    UPDATED;

    public static SortField parseOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequired(value);
    }

    public static SortField parseOrDefaultStars(String value) {
        if (value == null || value.isBlank()) {
            return STARS;
        }
        return parseRequired(value);
    }

    public String toGitHubSort() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Sort toSort() {
        String property = switch (this) {
            case FORKS -> "forks";
            case UPDATED -> "lastUpdated";
            case STARS -> "stars";
        };
        return Sort.by(Sort.Order.desc(property), Sort.Order.desc("githubId"));
    }

    private static SortField parseRequired(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "stars" -> STARS;
            case "forks" -> FORKS;
            case "updated" -> UPDATED;
            default -> throw new InvalidRequestException("sort must be one of: stars, forks, updated");
        };
    }
}
