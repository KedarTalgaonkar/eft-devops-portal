package com.euronet.eft.portal.github;

import com.euronet.eft.portal.integration.github.GithubClient;
import com.euronet.eft.portal.integration.github.GithubModels.CommitActivity;
import com.euronet.eft.portal.integration.github.GithubModels.PullRequestInfo;
import com.euronet.eft.portal.integration.github.GithubModels.RepositoryInfo;
import com.euronet.eft.portal.integration.github.GithubModels.TeamSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github")
@Tag(name = "GitHub", description = "Team-wise repositories, commit activity and pull requests")
public class GithubController {

    private final GithubClient github;

    public GithubController(GithubClient github) {
        this.github = github;
    }

    @GetMapping("/teams")
    @Operation(summary = "Teams with repository and open pull request counts")
    public List<TeamSummary> teams() {
        return github.teams();
    }

    @GetMapping("/teams/{team}/repos")
    @Operation(summary = "Repositories of one team")
    public List<RepositoryInfo> repositories(@PathVariable String team) {
        return github.repositories(team);
    }

    @GetMapping("/repos/{repo}/commits")
    @Operation(summary = "Commit activity of one repository per day and per author")
    public CommitActivity commits(@PathVariable String repo, @RequestParam(defaultValue = "30") int days) {
        return github.commits(repo, Math.clamp(days, 1, 365));
    }

    @GetMapping("/pulls")
    @Operation(summary = "Pull requests across the team repositories (state: open, closed, all)")
    public List<PullRequestInfo> pullRequests(@RequestParam(defaultValue = "open") String state) {
        return github.pullRequests(state);
    }
}
