package com.example.githubsearcher.repository;

import java.util.List;

public interface GitHubRepositoryCustom {

    int upsertAll(List<UpsertCommand> rows);
}
