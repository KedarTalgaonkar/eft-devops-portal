package com.euronet.eft.portal.inventory.importer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryExcelParserTest {

    private static InventoryWorkbook result;

    @BeforeAll
    static void parse() throws IOException {
        result = new InventoryExcelParser().parse(new ByteArrayInputStream(InventoryWorkbookFixture.twoOwners()));
    }

    private static ParsedDeployment find(String app, String env) {
        List<ParsedDeployment> matches = result.deployments().stream()
            .filter(d -> d.application().equals(app) && d.environment().equals(env))
            .toList();
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    @Test
    void readsInventorySheetsAndSkipsOthers() {
        assertThat(result.sheetsRead()).isEqualTo(2);
        assertThat(result.warnings()).singleElement().asString().contains("Notes");
    }

    @Test
    void createsOneDeploymentPerFilledEnvironmentBlock() {
        // QR ENGINE: SIT + UAT, IMPS Portal: SIT + UAT, ACS 2.0: UAT only
        assertThat(result.deployments()).hasSize(5);
        assertThat(result.deployments())
            .filteredOn(d -> d.application().equals("ACS 2.0"))
            .extracting(ParsedDeployment::environment)
            .containsExactly("UAT");
    }

    @Test
    void ownerComesFromSheetName() {
        assertThat(result.deployments())
            .filteredOn(d -> d.application().equals("QR ENGINE"))
            .extracting(ParsedDeployment::owner)
            .containsOnly("Samar");
    }

    @Test
    void cleansValues() {
        ParsedDeployment sitQr = find("QR ENGINE", "SIT");
        assertThat(sitQr.client()).isEqualTo("CBC");
        assertThat(sitQr.contextName()).isEqualTo("SITCBCQRAPI");
        assertThat(sitQr.javaVersion()).isEqualTo("8u145");      // numeric cell 8.1449999999999996
        assertThat(sitQr.tomcatVersion()).isEqualTo("8.032");
        assertThat(sitQr.tomcatAdminDone()).isTrue();

        ParsedDeployment uatQr = find("QR ENGINE", "UAT");
        assertThat(uatQr.baseUrl()).isEqualTo("http://uat-qr.example.internal:9085"); // trailing slash removed
        assertThat(uatQr.javaVersion()).isEqualTo("8u145");      // 1.8.0_145
        assertThat(uatQr.buildXmlDone()).isTrue();               // " Done"

        ParsedDeployment uatImps = find("IMPS Portal", "UAT");
        assertThat(uatImps.client()).isEqualTo("COSMOS BANK");   // trailing space trimmed
        assertThat(uatImps.baseUrl()).isEqualTo("http://uat-imps.example.internal:9045");
        assertThat(uatImps.tomcatVersion()).isEqualTo("9.0.67");
        assertThat(uatImps.javaVersion()).isEqualTo("8u311");
        assertThat(uatImps.buildXmlDone()).isFalse();            // "Pending"
    }
}
