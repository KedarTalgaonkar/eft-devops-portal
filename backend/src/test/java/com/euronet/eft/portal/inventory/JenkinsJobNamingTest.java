package com.euronet.eft.portal.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JenkinsJobNamingTest {

    private final JenkinsJobNaming naming = new JenkinsJobNaming("");

    private static Deployment deployment(String app, String client, String env) {
        Application a = new Application(app, client, "owner");
        return a.addDeployment(new Deployment(env, "ctx"));
    }

    @Test
    void uniqueNamesUseThePlainPattern() {
        Deployment qr = deployment("Prepaid QR", "CBC", "SIT");
        Set<String> shared = JenkinsJobNaming.sharedNames(List.of(qr));
        assertThat(naming.jobFor(qr, shared)).isEqualTo("prepaid-qr-SIT");
    }

    @Test
    void namesSharedByClientsGetTheClientSoJobsDoNotCollide() {
        Deployment cosmos = deployment("IMPS Portal", "COSMOS BANK", "SIT");
        Deployment nkgsb = deployment("IMPS Portal", "NKGSB BANK", "SIT");
        Set<String> shared = JenkinsJobNaming.sharedNames(List.of(cosmos, nkgsb));

        assertThat(naming.jobFor(cosmos, shared)).isEqualTo("imps-portal-cosmos-bank-SIT");
        assertThat(naming.jobFor(nkgsb, shared)).isEqualTo("imps-portal-nkgsb-bank-SIT");
    }

    @Test
    void explicitJobAndCustomPatternWin() {
        Deployment cosmos = deployment("IMPS Portal", "COSMOS BANK", "UAT");
        cosmos.setJenkinsJob("EFT/imps/cosmos-uat");
        assertThat(naming.jobFor(cosmos, Set.of("imps portal"))).isEqualTo("EFT/imps/cosmos-uat");

        JenkinsJobNaming folders = new JenkinsJobNaming("EFT/{client}/{app}/{env}");
        Deployment nkgsb = deployment("IMPS Portal", "NKGSB BANK", "UAT");
        assertThat(folders.jobFor(nkgsb, Set.of("imps portal"))).isEqualTo("EFT/nkgsb-bank/imps-portal/uat");
    }
}
