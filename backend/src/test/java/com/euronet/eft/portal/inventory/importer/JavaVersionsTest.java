package com.euronet.eft.portal.inventory.importer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class JavaVersionsTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
        "1.8.0_351, 8u351",
        "1.8.0_321, 8u321",
        "1.8.0_251, 8u251",
        "8.351, 8u351",
        "' 8.311', 8u311",
        "8.333, 8u333",
        "8.145, 8u145",
        "8.31, 8u310",
        "8.3510000000000009, 8u351",
        "17.0.12, 17.0.12",
        "21, 21",
    })
    void normalises(String raw, String expected) {
        assertThat(JavaVersions.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "'   '"})
    void blankIsNull(String raw) {
        assertThat(JavaVersions.normalize(raw)).isNull();
    }
}
