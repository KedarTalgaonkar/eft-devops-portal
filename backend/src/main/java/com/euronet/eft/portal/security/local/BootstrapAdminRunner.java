package com.euronet.eft.portal.security.local;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the first local administrator from PORTAL_BOOTSTRAP_ADMIN_PASSWORD when no local account exists yet. */
@Component
class BootstrapAdminRunner implements ApplicationRunner {

    private final LocalUserService users;

    BootstrapAdminRunner(LocalUserService users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.bootstrapAdminIfNeeded();
    }
}
