package com.euronet.eft.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static com.euronet.eft.portal.security.Xsrf.xsrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.euronet.eft.portal.security.local.LocalUserService;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end checks of the security layer against PostgreSQL (sessions, local accounts, audit). */
@Tag("db")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String TEMP = "Temp-Passw0rd!x";
    private static final String NEW = "Br4nd-New-Secret!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LocalUserService users;

    // ---------- access control ----------

    @Test
    void anonymousCallersGetJson401ButHealthIsPublic() throws Exception {
        mvc.perform(get("/api/pipelines/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void viewersReadButCannotChange() throws Exception {
        mvc.perform(get("/api/environments").with(user("v").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(post("/api/sbom/scans").with(user("v").roles("VIEWER")).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"projectName\":\"p\",\"projectVersion\":\"1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void changesNeedTheCsrfHeader() throws Exception {
        String body = "{\"projectName\":\"csrf-test\",\"projectVersion\":\"1.0\"}";
        mvc.perform(post("/api/sbom/scans").with(user("e").roles("EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sbom/scans").with(user("e").roles("EDITOR")).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void environmentAdministrationIsAdminOnly() throws Exception {
        String body = "{\"name\":\"SIT\"}";
        mvc.perform(put("/api/environments/SIT").with(user("e").roles("EDITOR")).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/environments/SIT").with(user("a").roles("ADMIN")).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").with(user("e").roles("EDITOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").with(user("a").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void apiResponsesCarrySecurityHeadersAndAreNotCached() throws Exception {
        mvc.perform(get("/api/environments").with(user("v").roles("VIEWER")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void sessionProbeIssuesTheXsrfCookieAndListsProviders() throws Exception {
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.providers.localEnabled").value(true))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    // ---------- local accounts ----------

    @Test
    void temporaryPasswordMustBeChangedBeforeUsingThePortal() throws Exception {
        String name = "temp-" + UUID.randomUUID().toString().substring(0, 8);
        users.create(name, "Temp User", null, EnumSet.of(PortalRole.VIEWER), TEMP);

        MvcResult login = mvc.perform(post("/api/auth/login").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"" + TEMP + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(cookie().httpOnly("EFTSESSION", true))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("EFTSESSION="),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax")))))
                .andReturn();
        Cookie session = login.getResponse().getCookie("EFTSESSION");
        assertThat(session).isNotNull();

        mvc.perform(get("/api/environments").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(post("/api/auth/password").cookie(session).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + TEMP + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/password").cookie(session).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + TEMP + "\",\"newPassword\":\"" + NEW + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        mvc.perform(get("/api/environments").cookie(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").cookie(session).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoutUrl").value(org.hamcrest.Matchers.endsWith("/login?signedOut")));
        mvc.perform(get("/api/environments").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordsLockTheAccountWithAGenericMessage() throws Exception {
        String name = "lock-" + UUID.randomUUID().toString().substring(0, 8);
        users.create(name, null, null, EnumSet.of(PortalRole.VIEWER), TEMP);

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + name + "\",\"password\":\"wrong-Passw0rd!\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password."));
        }
        // locked now: even the right password is refused, with the same message
        mvc.perform(post("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"" + TEMP + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
        // unknown users look exactly the same
        mvc.perform(post("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody-here\",\"password\":\"" + TEMP + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        mvc.perform(get("/api/admin/audit").with(user("a").roles("ADMIN")))
                .andExpect(jsonPath("$[?(@.action == 'ACCOUNT_LOCKED' && @.target == '" + name + "')]").isNotEmpty());
    }

    @Test
    void loginRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isForbidden());
    }
}
