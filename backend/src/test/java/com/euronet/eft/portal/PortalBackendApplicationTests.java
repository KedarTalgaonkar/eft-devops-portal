package com.euronet.eft.portal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context against PostgreSQL (Flyway migrations + Hibernate schema validation).
 */
@Tag("db")
@SpringBootTest
class PortalBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
