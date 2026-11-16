package com.euronet.eft.portal.integration.jenkins;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BuildLogTest {

    @Test
    void keepsTheTailAndEarlierErrorLinesOnly() {
        StringBuilder sb = new StringBuilder("[ERROR] first real error in Foo.java\n");
        for (int i = 0; i < 5_000; i++) {
            sb.append("[INFO] noise line ").append(i).append('\n');
        }
        sb.append("[ERROR] BUILD FAILURE\nFinished: FAILURE\n");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        BuildLog log = BuildLog.read(new ByteArrayInputStream(bytes), 4096);

        assertThat(log.truncated()).isTrue();
        assertThat(log.totalBytes()).isEqualTo(bytes.length);
        assertThat(log.tail().length()).isLessThanOrEqualTo(4096);
        assertThat(log.tail()).endsWith("Finished: FAILURE").doesNotContain("first real error");
        assertThat(log.earlyErrors()).containsExactly("[ERROR] first real error in Foo.java");
    }

    @Test
    void shortLogsAreKeptWhole() {
        BuildLog log = BuildLog.read(new ByteArrayInputStream("a\n[ERROR] b\nc".getBytes(StandardCharsets.UTF_8)), 4096);
        assertThat(log.truncated()).isFalse();
        assertThat(log.tail()).isEqualTo("a\n[ERROR] b\nc");
        assertThat(log.earlyErrors()).isEmpty();
    }
}
