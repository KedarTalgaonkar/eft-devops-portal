package com.euronet.eft.portal.inventory;

import com.euronet.eft.portal.common.Slugs;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides which Jenkins job tracks an application in an environment: the job set on the deployment,
 * otherwise the configured pattern, e.g. {@code {app}-{ENV}} gives {@code prepaid-qr-SIT}.
 * <p>
 * When the same application name exists for several clients (IMPS Portal for COSMOS BANK and NKGSB BANK),
 * a pattern without {@code {client}} would map them all to one job, so the client is added automatically:
 * {@code imps-portal-cosmos-bank-SIT}.
 */
@Component
public class JenkinsJobNaming {

    static final String DEFAULT_PATTERN = "{app}-{ENV}";

    private final String pattern;

    public JenkinsJobNaming(@Value("${portal.pipelines.job-pattern:}") String pattern) {
        this.pattern = pattern == null || pattern.isBlank() ? DEFAULT_PATTERN : pattern.strip();
    }

    /** Names (lower case) used by more than one client, from any collection of deployments. */
    public static Set<String> sharedNames(Collection<Deployment> deployments) {
        Map<String, Set<String>> clientsByName = new HashMap<>();
        for (Deployment d : deployments) {
            clientsByName.computeIfAbsent(d.getApplication().getName().toLowerCase(Locale.ROOT), k -> new HashSet<>())
                    .add(d.getApplication().getClient().toLowerCase(Locale.ROOT));
        }
        Set<String> shared = new HashSet<>();
        clientsByName.forEach((name, clients) -> {
            if (clients.size() > 1) {
                shared.add(name);
            }
        });
        return shared;
    }

    public String jobFor(Deployment deployment, Set<String> sharedNames) {
        if (deployment.getJenkinsJob() != null && !deployment.getJenkinsJob().isBlank()) {
            return deployment.getJenkinsJob().strip();
        }
        return fromPattern(deployment.getApplication(), deployment.getEnvironment(), sharedNames);
    }

    public String fromPattern(Application app, String environment, Set<String> sharedNames) {
        String effective = pattern;
        if (!effective.contains("{client}") && !app.getClient().isBlank()
                && sharedNames.contains(app.getName().toLowerCase(Locale.ROOT))) {
            effective = effective.replace("{app}", "{app}-{client}");
        }
        return effective
                .replace("{app}", Slugs.slug(app.getName()))
                .replace("{client}", Slugs.slug(app.getClient()))
                .replace("{ENV}", environment)
                .replace("{env}", environment.toLowerCase(Locale.ROOT));
    }

    /** Pattern-only name, for callers that know the application name is unique. */
    public String fromPattern(Application app, String environment) {
        return fromPattern(app, environment, Set.of());
    }
}
