package com.euronet.eft.portal.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    private final Redactor redactor = new Redactor(List.of(".internal", "corp"), List.of("tomcat-uat-07", "198.51.100.23"));

    @Test
    void masksIpsAndInternalHostsWithStablePlaceholdersAndRestoresThem() {
        String log = "Deploying to http://tomcat-sit-02.example.internal:9091 (192.0.2.41)\n"
            + "curl: (7) Failed to connect to 192.0.2.41 port 9091\n"
            + "fallback build.corp and tomcat-uat-07 at 198.51.100.23";
        Redactor.Redaction r = redactor.redact(log);

        assertThat(r.text())
            .doesNotContain("192.0.2.41", "tomcat-sit-02.example.internal", "build.corp", "tomcat-uat-07", "198.51.100.23")
            .containsPattern("http://\\[HOST_\\d]:9091");
        // the same IP gets the same placeholder everywhere
        String[] lines = r.text().split("\n");
        String first = lines[0].replaceAll(".*\\((\\[IP_\\d])\\).*", "$1");
        assertThat(lines[1]).contains("Failed to connect to " + first + " port 9091");
        assertThat(r.restore(r.text())).isEqualTo(log);
        assertThat(r.counts()).containsEntry("IP addresses", 2).containsEntry("host names", 3);
        assertThat(r.summary()).contains("2 IP addresses").contains("3 host names");
    }

    @Test
    void masksSecretsAndNeverRestoresThem() {
        String log = String.join("\n",
            "db.password=Sup3r$ecret",
            "\"client_secret\": \"abc123def\"",
            "Authorization: Bearer eyJhbGciOi.eyJzdWIiOiIx.c2lnbmF0dXJl",
            "git clone https://deployer:Pa55word@github.example.internal/eft/app.git",
            "mvn deploy -Dartifactory.apiKey=AKCp8jQ8something",
            "./deploy.sh --password hunter2 --verbose",
            "token: ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
            "AWS key AKIAABCDEFGHIJKLMNOP",
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEow\n-----END RSA PRIVATE KEY-----");
        Redactor.Redaction r = redactor.redact(log);

        assertThat(r.text()).doesNotContain("Sup3r$ecret", "abc123def", "eyJhbGciOi", "Pa55word", "AKCp8jQ8something",
            "hunter2", "ghp_ABCDEF", "AKIAABCDEFGHIJKLMNOP", "MIIEow");
        assertThat(r.text()).contains("--verbose").contains("db.password=[SECRET]");
        assertThat(r.restore(r.text())).doesNotContain("Sup3r$ecret", "hunter2");
        assertThat(r.counts().get("secrets")).isGreaterThanOrEqualTo(9);
    }

    @Test
    void masksEmailAddresses() {
        Redactor.Redaction r = redactor.redact("Contact middleware-oncall@example.internal or a.b@euronet.example");
        assertThat(r.text()).isEqualTo("Contact [EMAIL_1] or [EMAIL_2]");
        assertThat(r.restore("Ask [EMAIL_2]")).isEqualTo("Ask a.b@euronet.example");
    }

    @Test
    void leavesOrdinaryBuildOutputAlone() {
        String log = String.join("\n",
            "[INFO] Building prepaid-qr 1.4.2",
            "Downloaded from central: https://repo.maven.apache.org/maven2/org/springframework/spring-core/6.2.1/x.jar",
            "Unexpected token in JSON at position 12",
            "Tests run: 48, Failures: 0, Errors: 0, Skipped: 0",
            "java.lang.NullPointerException at com.euronet.eft.Switch.route(Switch.java:42)");
        Redactor.Redaction r = redactor.redact(log);
        assertThat(r.text()).isEqualTo(log);
        assertThat(r.summary()).isEqualTo("nothing to mask");
    }
}
