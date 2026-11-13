package com.euronet.eft.portal.inventory.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

/** New environments, owner and repository columns import without code changes. */
class ScalableLayoutParserTest {

    @Test
    void readsAnyEnvironmentGroupAndOptionalColumns() throws Exception {
        InventoryWorkbook wb = new InventoryExcelParser().parse(new ByteArrayInputStream(
                InventoryWorkbookFixture.scalable("Deepak", "http://preprod-recon.example.internal:8080/")));

        assertThat(wb.warnings()).isEmpty();
        assertThat(wb.deployments()).extracting(ParsedDeployment::environment).containsExactly("PERF", "PREPROD");
        ParsedDeployment perf = wb.deployments().getFirst();
        assertThat(perf.owner()).isEqualTo("Deepak");                  // Owner column wins over the sheet name
        assertThat(perf.repositories()).containsExactly("eft-org/recon-engine", "eft-org/recon-common");
        assertThat(perf.javaVersion()).isEqualTo("21.0.5");
        assertThat(wb.deployments().getLast().buildXmlDone()).isFalse();
    }
}
