package com.euronet.eft.portal.inventory;

import com.euronet.eft.portal.common.EnvironmentCodes;
import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.environment.EnvironmentCatalog;
import com.euronet.eft.portal.inventory.InventoryChange.Source;
import com.euronet.eft.portal.inventory.InventoryChange.Type;
import com.euronet.eft.portal.inventory.InventoryDtos.ApplicationDetail;
import com.euronet.eft.portal.inventory.InventoryDtos.ApplicationSummary;
import com.euronet.eft.portal.inventory.InventoryDtos.ChangeView;
import com.euronet.eft.portal.inventory.InventoryDtos.DeploymentView;
import com.euronet.eft.portal.inventory.InventoryDtos.ImportResult;
import com.euronet.eft.portal.inventory.importer.InventoryExcelParser;
import com.euronet.eft.portal.inventory.importer.InventoryWorkbook;
import com.euronet.eft.portal.inventory.importer.ParsedDeployment;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ApplicationRepository applications;
    private final DeploymentRepository deployments;
    private final InventoryChangeRepository changes;
    private final InventoryExcelParser parser;
    private final JenkinsJobNaming jobNaming;
    private final EnvironmentCatalog environments;

    public InventoryService(ApplicationRepository applications, DeploymentRepository deployments,
                            InventoryChangeRepository changes, InventoryExcelParser parser,
                            JenkinsJobNaming jobNaming, EnvironmentCatalog environments) {
        this.applications = applications;
        this.deployments = deployments;
        this.changes = changes;
        this.parser = parser;
        this.jobNaming = jobNaming;
        this.environments = environments;
    }

    public List<ApplicationSummary> listApplications() {
        Comparator<String> envOrder = environments.order();
        return applications.findAllWithDeployments().stream()
                .map(a -> new ApplicationSummary(
                        a.getId(), a.getName(), a.getClient(), a.getOwner(),
                        a.getDeployments().size(),
                        a.getDeployments().stream().map(Deployment::getEnvironment).distinct().sorted(envOrder).toList(),
                        List.copyOf(a.getRepositories()),
                        a.getUpdatedAt()))
                .toList();
    }

    public ApplicationDetail getApplication(long id) {
        Application a = applications.findById(id).orElseThrow(() -> new NotFoundException("Application", id));
        java.util.Set<String> shared = sharedNames();
        Comparator<String> envOrder = environments.order();
        return new ApplicationDetail(a.getId(), a.getName(), a.getClient(), a.getOwner(), a.getUpdatedAt(),
                List.copyOf(a.getRepositories()),
                a.getDeployments().stream()
                        .sorted(Comparator.comparing(Deployment::getEnvironment, envOrder).thenComparing(Deployment::getContextName))
                        .map(d -> toView(d, shared)).toList());
    }

    /** Flat list of deployments, optionally for one environment code. */
    public List<DeploymentView> listDeployments(String environment) {
        String env = environment == null || environment.isBlank() ? null : EnvironmentCodes.require(environment);
        Comparator<String> envOrder = environments.order();
        List<Deployment> all = deployments.findAllWithApplication();
        java.util.Set<String> shared = JenkinsJobNaming.sharedNames(all);
        return all.stream()
                .filter(d -> env == null || d.getEnvironment().equals(env))
                .sorted(Comparator.comparing((Deployment d) -> d.getApplication().getName().toLowerCase(Locale.ROOT))
                        .thenComparing(d -> d.getApplication().getClient().toLowerCase(Locale.ROOT))
                        .thenComparing(Deployment::getEnvironment, envOrder)
                        .thenComparing(Deployment::getContextName))
                .map(d -> toView(d, shared))
                .toList();
    }

    /** Every deployment entity with its application loaded; used by the pipelines and dashboard modules. */
    public List<Deployment> allDeployments() {
        return deployments.findAllWithApplication();
    }

    public List<ChangeView> changes(Long applicationId, int limit) {
        Limit max = Limit.of(Math.clamp(limit, 1, 1000));
        List<InventoryChange> list = applicationId == null
                ? changes.findAllByOrderByChangedAtDescIdDesc(max)
                : changes.findByApplicationIdOrderByChangedAtDescIdDesc(applicationId, max);
        Map<Long, Application> apps = applications.findAllById(
                        list.stream().map(InventoryChange::getApplicationId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Application::getId, Function.identity()));
        Map<Long, Deployment> deps = deployments.findAllById(
                        list.stream().map(InventoryChange::getDeploymentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Deployment::getId, Function.identity()));
        return list.stream().map(c -> {
            Application a = apps.get(c.getApplicationId());
            Deployment d = deps.get(c.getDeploymentId());
            return new ChangeView(c.getId(), c.getApplicationId(), a == null ? null : a.getName(), a == null ? null : a.getClient(),
                    c.getDeploymentId(), d == null ? null : d.getEnvironment(), d == null ? null : d.getContextName(),
                    c.getChangeType(), c.getField(), c.getOldValue(), c.getNewValue(), c.getSource(), c.getChangedAt());
        }).toList();
    }

    @Transactional
    public DeploymentView updateJenkinsJob(long deploymentId, String jenkinsJob) {
        Deployment d = deployments.findById(deploymentId)
                .orElseThrow(() -> new NotFoundException("Deployment", deploymentId));
        String value = jenkinsJob == null || jenkinsJob.isBlank() ? null : jenkinsJob.strip();
        if (!Objects.equals(d.getJenkinsJob(), value)) {
            changes.save(new InventoryChange(d.getApplication().getId(), d.getId(), Type.FIELD_CHANGED, "jenkinsJob",
                    d.getJenkinsJob(), value, Source.PORTAL));
            d.setJenkinsJob(value);
        }
        return toView(d, sharedNames());
    }

    @Transactional
    public ApplicationDetail updateRepositories(long applicationId, List<String> repositories) {
        Application a = applications.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application", applicationId));
        Set<String> next = new LinkedHashSet<>();
        repositories.stream().map(String::strip).filter(r -> !r.isEmpty()).forEach(next::add);
        if (!next.equals(a.getRepositories())) {
            changes.save(new InventoryChange(a.getId(), null, Type.FIELD_CHANGED, "repositories",
                    String.join(", ", a.getRepositories()), String.join(", ", next), Source.PORTAL));
            a.getRepositories().clear();
            a.getRepositories().addAll(next);
        }
        return getApplication(applicationId);
    }

    /**
     * Imports the inventory workbook. Additive and idempotent: applications match on name + client,
     * deployments on environment + context. New environments are registered, owner moves and changed
     * values are written to the change log, and Jenkins jobs set in the portal are kept.
     */
    @Transactional
    public ImportResult importWorkbook(InputStream in, String fileName) throws IOException {
        InventoryWorkbook workbook = parser.parse(in);
        Set<String> knownEnvironments = new HashSet<>(environments.list().stream().map(EnvironmentCatalog.EnvironmentView::code).toList());
        List<String> newEnvironments = new ArrayList<>();
        Map<String, Application> byKey = new HashMap<>();
        Set<Long> appsTouched = new HashSet<>();
        int appsCreated = 0;
        int appsUpdated = 0;
        int depsCreated = 0;
        int depsUpdated = 0;

        for (ParsedDeployment row : workbook.deployments()) {
            String env = environments.ensure(row.environment());
            if (knownEnvironments.add(env)) {
                newEnvironments.add(env);
            }

            String key = row.application().toLowerCase(Locale.ROOT) + "|" + row.client().toLowerCase(Locale.ROOT);
            Application app = byKey.get(key);
            if (app == null) {
                app = applications.findByNameAndClient(row.application(), row.client()).orElse(null);
                if (app == null) {
                    app = applications.save(new Application(row.application(), row.client(), row.owner()));
                    changes.save(new InventoryChange(app.getId(), null, Type.APPLICATION_ADDED, null, null,
                            app.getName() + " / " + app.getClient(), Source.IMPORT));
                    appsCreated++;
                    appsTouched.add(app.getId());
                } else if (!Objects.equals(app.getOwner(), row.owner())) {
                    changes.save(new InventoryChange(app.getId(), null, Type.FIELD_CHANGED, "owner",
                            app.getOwner(), row.owner(), Source.IMPORT));
                    app.setOwner(row.owner());
                    if (appsTouched.add(app.getId())) {
                        appsUpdated++;
                    }
                }
                byKey.put(key, app);
            }
            for (String repo : row.repositories()) {
                if (app.getRepositories().add(repo)) {
                    changes.save(new InventoryChange(app.getId(), null, Type.FIELD_CHANGED, "repositories",
                            null, repo, Source.IMPORT));
                }
            }

            Deployment deployment = app.getDeployments().stream()
                    .filter(d -> d.getEnvironment().equals(env) && d.getContextName().equalsIgnoreCase(row.contextName()))
                    .findFirst()
                    .orElse(null);
            boolean created = deployment == null;
            if (created) {
                deployment = app.addDeployment(new Deployment(env, row.contextName()));
                deployments.saveAndFlush(deployment);
                changes.save(new InventoryChange(app.getId(), deployment.getId(), Type.DEPLOYMENT_ADDED, null, null,
                        env + " " + row.contextName() + " " + Objects.toString(row.baseUrl(), ""), Source.IMPORT));
                depsCreated++;
            }
            boolean changed = false;
            changed |= set(deployment, "baseUrl", deployment.getBaseUrl(), row.baseUrl(), created, deployment::setBaseUrl);
            changed |= set(deployment, "tomcatVersion", deployment.getTomcatVersion(), row.tomcatVersion(), created, deployment::setTomcatVersion);
            changed |= set(deployment, "javaVersion", deployment.getJavaVersion(), row.javaVersion(), created, deployment::setJavaVersion);
            changed |= setFlag(deployment, "tomcatAdminDone", deployment.isTomcatAdminDone(), row.tomcatAdminDone(), created, deployment::setTomcatAdminDone);
            changed |= setFlag(deployment, "buildXmlDone", deployment.isBuildXmlDone(), row.buildXmlDone(), created, deployment::setBuildXmlDone);
            if (changed && !created) {
                depsUpdated++;
            }
        }

        log.info("Imported {}: {} sheets, {} rows, apps +{} ~{}, deployments +{} ~{}, new environments {}", fileName,
                workbook.sheetsRead(), workbook.deployments().size(), appsCreated, appsUpdated, depsCreated, depsUpdated, newEnvironments);
        return new ImportResult(fileName, workbook.sheetsRead(), workbook.deployments().size(),
                appsCreated, appsUpdated, depsCreated, depsUpdated, newEnvironments, workbook.warnings());
    }

    private boolean set(Deployment d, String field, String oldValue, String newValue, boolean created,
                        java.util.function.Consumer<String> setter) {
        if (Objects.equals(oldValue, newValue)) {
            return false;
        }
        setter.accept(newValue);
        if (!created) {
            changes.save(new InventoryChange(d.getApplication().getId(), d.getId(), Type.FIELD_CHANGED, field,
                    oldValue, newValue, Source.IMPORT));
        }
        return true;
    }

    private boolean setFlag(Deployment d, String field, boolean oldValue, boolean newValue, boolean created,
                            java.util.function.Consumer<Boolean> setter) {
        if (oldValue == newValue) {
            return false;
        }
        setter.accept(newValue);
        if (!created) {
            changes.save(new InventoryChange(d.getApplication().getId(), d.getId(), Type.FIELD_CHANGED, field,
                    String.valueOf(oldValue), String.valueOf(newValue), Source.IMPORT));
        }
        return true;
    }

    /** Application names used by more than one client (their default Jenkins job names include the client). */
    private java.util.Set<String> sharedNames() {
        return JenkinsJobNaming.sharedNames(deployments.findAllWithApplication());
    }

    DeploymentView toView(Deployment d, java.util.Set<String> sharedNames) {
        Application a = d.getApplication();
        return new DeploymentView(d.getId(), a.getId(), a.getName(), a.getClient(), a.getOwner(),
                d.getEnvironment(), d.getPlatform(), d.getContextName(), d.getBaseUrl(), d.contextUrl(),
                d.getTomcatVersion(), d.getJavaVersion(), d.isTomcatAdminDone(), d.isBuildXmlDone(),
                d.getJenkinsJob(), jobNaming.jobFor(d, sharedNames), List.copyOf(a.getRepositories()), d.getUpdatedAt());
    }
}
