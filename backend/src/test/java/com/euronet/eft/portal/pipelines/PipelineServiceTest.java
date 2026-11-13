package com.euronet.eft.portal.pipelines;

import static com.euronet.eft.portal.pipelines.BuildStatsTest.build;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.euronet.eft.portal.environment.EnvironmentCatalog;
import com.euronet.eft.portal.integration.jenkins.BuildResult;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuild;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuildCache;
import com.euronet.eft.portal.inventory.Application;
import com.euronet.eft.portal.inventory.Deployment;
import com.euronet.eft.portal.inventory.InventoryService;
import com.euronet.eft.portal.inventory.JenkinsJobNaming;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineMetric;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PipelineServiceTest {

    private final InventoryService inventory = mock(InventoryService.class);
    private final JenkinsBuildCache builds = mock(JenkinsBuildCache.class);
    private PipelineService service;

    @BeforeEach
    void setUp() {
        Application qr = app(1L, "Prepaid QR", "CBC");
        Application recon = app(2L, "Recon", "Euronet");
        Deployment qrSit1 = qr.addDeployment(new Deployment("SIT", "qrapi"));
        qr.addDeployment(new Deployment("SIT", "qrportal"));          // same pipeline as qrSit1
        qr.addDeployment(new Deployment("UAT", "qrapi"));
        Deployment reconSit = recon.addDeployment(new Deployment("SIT", "recon"));
        reconSit.setJenkinsJob("EFT/recon-sit-deploy");                          // explicit job wins over the pattern
        when(inventory.allDeployments()).thenReturn(List.of(qrSit1, qr.getDeployments().get(1),
                qr.getDeployments().get(2), reconSit));

        Instant now = Instant.now();
        when(builds.builds("prepaid-qr-SIT")).thenReturn(List.of(
                at(build(5, BuildResult.IN_PROGRESS), now),
                at(build(4, BuildResult.SUCCESS), now.minus(Duration.ofDays(1))),
                at(build(3, BuildResult.FAILURE), now.minus(Duration.ofDays(2))),
                at(build(2, BuildResult.ABORTED), now.minus(Duration.ofDays(3))),
                at(build(1, BuildResult.SUCCESS), now.minus(Duration.ofDays(40))))); // outside 30 days
        when(builds.builds("prepaid-qr-UAT")).thenReturn(List.of());
        when(builds.builds("EFT/recon-sit-deploy")).thenThrow(new IllegalStateException("Jenkins down"));

        EnvironmentCatalog catalog = mock(EnvironmentCatalog.class);
        when(catalog.order()).thenReturn(Comparator.naturalOrder());
        service = new PipelineService(inventory, builds, new JenkinsJobNaming(""), catalog);
    }

    @Test
    void oneRowPerProjectAndEnvironmentWithCountsInWindow() {
        List<PipelineMetric> rows = service.metrics(null, 30);

        assertThat(rows).extracting(PipelineMetric::project, PipelineMetric::environment, PipelineMetric::jenkinsJob)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Prepaid QR", "SIT", "prepaid-qr-SIT"),
                        org.assertj.core.groups.Tuple.tuple("Prepaid QR", "UAT", "prepaid-qr-UAT"),
                        org.assertj.core.groups.Tuple.tuple("Recon", "SIT", "EFT/recon-sit-deploy"));

        PipelineMetric qrSit = rows.getFirst();
        assertThat(qrSit.totalBuilds()).isEqualTo(4);
        assertThat(qrSit.success()).isEqualTo(1);
        assertThat(qrSit.failed()).isEqualTo(1);
        assertThat(qrSit.aborted()).isEqualTo(1);
        assertThat(qrSit.inProgress()).isEqualTo(1);
        assertThat(qrSit.successRate()).isEqualTo(33.3);
        assertThat(qrSit.lastBuild().number()).isEqualTo(5);
    }

    @Test
    void failingJobDoesNotBreakTheTable() {
        PipelineMetric recon = service.metrics("SIT", 30).getLast();
        assertThat(recon.project()).isEqualTo("Recon");
        assertThat(recon.totalBuilds()).isZero();
        assertThat(recon.error()).contains("Jenkins down");
    }

    @Test
    void filtersByEnvironment() {
        assertThat(service.metrics("UAT", 30)).extracting(PipelineMetric::project).containsExactly("Prepaid QR");
    }

    @Test
    void deploymentHistoryHasOnlySuccessfulDeploysNewestFirst() {
        var history = service.deploymentHistory("SIT", 30, 10);
        assertThat(history).extracting(e -> e.buildNumber()).containsExactly(4);
    }

    private static Application app(long id, String name, String client) {
        Application a = new Application(name, client, "owner");
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private static JenkinsBuild at(JenkinsBuild b, Instant started) {
        return new JenkinsBuild(b.job(), b.number(), b.result(), started, b.duration(), b.triggeredBy(), b.version(),
                b.deployment(), b.url());
    }
}
