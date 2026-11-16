package com.euronet.eft.portal.integration.github;

import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.integration.IntegrationStatus;
import com.euronet.eft.portal.integration.github.GithubModels.AuthorCount;
import com.euronet.eft.portal.integration.github.GithubModels.CommitActivity;
import com.euronet.eft.portal.integration.github.GithubModels.DailyCount;
import com.euronet.eft.portal.integration.github.GithubModels.PullRequestInfo;
import com.euronet.eft.portal.integration.github.GithubModels.RepositoryInfo;
import com.euronet.eft.portal.integration.github.GithubModels.TeamSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@ConditionalOnProperty(prefix = "portal.integrations.github", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockGithubClient implements GithubClient {

    private static final String ORG = "eft-org";
    private static final String[] AUTHORS = {"narsing", "shweta", "deepak", "ganesh", "samar", "dependabot[bot]"};
    private static final String[] TITLES = {
        "Fix null check in settlement batch", "Bump Tomcat to 9.0.98", "Add SIT config for QR engine",
        "Refactor card activation API", "Upgrade log4j", "Add retry to SMS alert sender",
        "Jenkinsfile: add UAT deploy stage", "Sonar: remove duplicated mapper code"};

    private static final Map<String, List<String>> TEAMS = new LinkedHashMap<>();

    static {
        TEAMS.put("eft-prepaid", List.of("ppi-branch", "ppi-activation-api", "ppi-customer-api", "prepaid-qr-api", "prepaid-axis", "ria-portal"));
        TEAMS.put("eft-payments", List.of("imps-portal", "rupay-ecom", "e-mandate", "transakto-engine", "transakto-admin-portal"));
        TEAMS.put("eft-switching", List.of("acs-2", "qr-engine", "recon"));
        TEAMS.put("eft-devops", List.of("jenkins-shared-library", "tomcat-ansible", "eft-devops-portal"));
    }

    private static String title(String slug) {
        String rest = slug.substring(slug.indexOf('-') + 1);
        return "EFT " + Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
    }

    @Override
    public List<TeamSummary> teams() {
        return TEAMS.entrySet().stream().map(e -> {
            Random r = new Random(e.getKey().hashCode());
            int prs = repositories(e.getKey()).stream().mapToInt(RepositoryInfo::openPullRequests).sum();
            return new TeamSummary(e.getKey(), title(e.getKey()), 3 + r.nextInt(9), e.getValue().size(), prs);
        }).toList();
    }

    @Override
    public List<RepositoryInfo> repositories(String team) {
        List<String> repos = TEAMS.get(team);
        if (repos == null) {
            throw new NotFoundException("GitHub team", team);
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        return repos.stream().map(name -> {
            Random r = new Random(name.hashCode());
            return new RepositoryInfo(name, ORG + "/" + name, team, r.nextInt(4) == 0 ? "master" : "main",
                name.contains("portal") && r.nextBoolean() ? "TypeScript" : "Java", "internal", false,
                r.nextInt(6), commits(name, 30).totalCommits(),
                now.minus(r.nextInt(14 * 24), ChronoUnit.HOURS), "https://github.example.internal/" + ORG + "/" + name);
        }).toList();
    }

    @Override
    public CommitActivity commits(String repository, int days) {
        Random r = new Random(repository.hashCode());
        int[] authorTotals = new int[AUTHORS.length];
        List<DailyCount> perDay = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int total = 0;
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            boolean weekend = date.getDayOfWeek().getValue() >= 6;
            int n = weekend ? r.nextInt(2) : r.nextInt(7);
            for (int c = 0; c < n; c++) {
                authorTotals[r.nextInt(AUTHORS.length)]++;
            }
            perDay.add(new DailyCount(date, n));
            total += n;
        }
        List<AuthorCount> authors = new ArrayList<>();
        for (int i = 0; i < AUTHORS.length; i++) {
            if (authorTotals[i] > 0) {
                authors.add(new AuthorCount(AUTHORS[i], authorTotals[i]));
            }
        }
        authors.sort(Comparator.comparingInt(AuthorCount::commits).reversed());
        return new CommitActivity(repository, days, total, perDay, authors);
    }

    @Override
    public List<PullRequestInfo> pullRequests(String state) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        List<PullRequestInfo> prs = new ArrayList<>();
        for (List<String> repos : TEAMS.values()) {
            for (String repo : repos) {
                Random r = new Random(repo.hashCode() + 7L);
                int count = 1 + r.nextInt(5);
                for (int i = 0; i < count; i++) {
                    String prState = r.nextInt(3) == 0 ? "closed" : "open";
                    Instant created = now.minus(1 + r.nextInt(30 * 24), ChronoUnit.HOURS);
                    int number = 10 + r.nextInt(400);
                    prs.add(new PullRequestInfo(repo, number, TITLES[r.nextInt(TITLES.length)],
                        AUTHORS[r.nextInt(AUTHORS.length)], prState, r.nextInt(6) == 0, r.nextInt(4), created,
                        created.plus(r.nextInt(48), ChronoUnit.HOURS),
                        "https://github.example.internal/" + ORG + "/" + repo + "/pull/" + number));
                }
            }
        }
        return prs.stream()
            .filter(p -> state == null || state.equalsIgnoreCase("all") || p.state().equalsIgnoreCase(state))
            .sorted(Comparator.comparing(PullRequestInfo::updatedAt).reversed())
            .toList();
    }

    @Override
    public IntegrationStatus status() {
        return IntegrationStatus.mock("github", "GitHub");
    }
}
