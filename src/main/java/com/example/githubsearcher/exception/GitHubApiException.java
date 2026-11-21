package com.example.githubsearcher.exception;

public class GitHubApiException extends RuntimeException {

    private final int status;

    public GitHubApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    public GitHubApiException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
