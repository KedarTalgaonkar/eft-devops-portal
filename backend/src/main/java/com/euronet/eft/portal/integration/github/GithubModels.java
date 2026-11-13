package com.euronet.eft.portal.integration.github;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** GitHub shapes used by the portal (a subset of the GitHub REST API). */
public final class GithubModels {

    private GithubModels() {
    }

    public record TeamSummary(String slug, String name, int members, int repositoryCount, int openPullRequests) {
    }

    public record RepositoryInfo(String name, String fullName, String team, String defaultBranch, String language,
                                 String visibility, boolean archived, int openPullRequests, int commitsLast30Days,
                                 Instant lastPushAt, String url) {
    }

    public record DailyCount(LocalDate date, int commits) {
    }

    public record AuthorCount(String author, int commits) {
    }

    public record CommitActivity(String repository, int days, int totalCommits, List<DailyCount> perDay, List<AuthorCount> topAuthors) {
    }

    public record PullRequestInfo(String repository, int number, String title, String author, String state,
                                  boolean draft, int reviewers, Instant createdAt, Instant updatedAt, String url) {
    }
}
