package com.example.githubsearcher.exception;

public class GitHubRateLimitException extends RuntimeException {

    private final String retryAfter;

    public GitHubRateLimitException(String message, String retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}
