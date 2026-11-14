package com.euronet.eft.portal.ai.builds;

import static org.assertj.core.api.Assertions.assertThat;

import com.euronet.eft.portal.ai.Redactor;
import com.euronet.eft.portal.ai.builds.BuildDiagnosis.Category;
import com.euronet.eft.portal.integration.jenkins.MockJenkinsClient;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The mock pipeline: sample Jenkins log -> masking -> rule-based diagnosis covers every sample scenario. */
class MockBuildDiagnoserTest {

    @Test
    void everySampleFailureGetsAKnownCategoryAndMaskedEvidence() {
        MockJenkinsClient jenkins = new MockJenkinsClient();
        Redactor redactor = new Redactor(List.of(".internal"), List.of());
        Set<Category> seen = EnumSet.noneOf(Category.class);

        for (int number = 100; number < 107; number++) { // 7 consecutive builds = all 7 scenarios
            String log = jenkins.log("EFT/prepaid-qr-SIT", number, 64 * 1024).tail();
            Redactor.Redaction masked = redactor.redact(log);
            BuildDiagnosis d = MockBuildDiagnoser.diagnose(masked.text());

            assertThat(d.category()).isNotEqualTo(Category.UNKNOWN);
            assertThat(d.evidence()).isNotEmpty();
            assertThat(String.join("\n", d.evidence())).doesNotContain("192.0.2.41", "Art1factory#2026", "example.internal");
            seen.add(d.category());
        }
        assertThat(seen).containsExactlyInAnyOrder(Category.COMPILE, Category.TEST, Category.DEPENDENCY, Category.DEPLOYMENT,
                Category.QUALITY_GATE, Category.TIMEOUT, Category.INFRASTRUCTURE);
    }

    @Test
    void unknownLogsAreReportedAsUnknownWithLowConfidence() {
        BuildDiagnosis d = MockBuildDiagnoser.diagnose("Started\nsomething odd happened\nFinished: FAILURE");
        assertThat(d.category()).isEqualTo(Category.UNKNOWN);
        assertThat(d.confidence()).isEqualTo(BuildDiagnosis.Confidence.LOW);
    }
}
