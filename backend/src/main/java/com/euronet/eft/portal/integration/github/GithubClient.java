package com.euronet.eft.portal.integration.github;

import com.euronet.eft.portal.integration.IntegrationHealth;
import com.euronet.eft.portal.integration.github.GithubModels.CommitActivity;
import com.euronet.eft.portal.integration.github.GithubModels.PullRequestInfo;
import com.euronet.eft.portal.integration.github.GithubModels.RepositoryInfo;
import com.euronet.eft.portal.integration.github.GithubModels.TeamSummary;

import java.util.List;

/**
 * Team-wise repositories, commit activity and pull requests. Live client: phase 2 (REST/GraphQL with a token).
 */
public interface GithubClient extends IntegrationHealth {

    List<TeamSummary> teams();

    List<RepositoryInfo> repositories(String team);

    CommitActivity commits(String repository, int days);

    /**
     * @param state open, closed or all
     */
    List<PullRequestInfo> pullRequests(String state);
}
