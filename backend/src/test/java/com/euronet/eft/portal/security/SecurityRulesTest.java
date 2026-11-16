package com.euronet.eft.portal.security;

import com.euronet.eft.portal.security.local.PasswordPolicy;
import com.euronet.eft.portal.security.oidc.PortalOidcUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"//evil.example", "https://evil.example/x", "/\\evil.example", "javascript:alert(1)",
        "/api/admin/users", "/login?x", "/oauth2/authorization/sso", "relative/path", ""})
    void unsafeReturnTargetsFallBackToRoot(String target) {
        assertThat(SafeRedirects.relativeOrRoot(target)).isEqualTo("/");
    }

    @Test
    void portalPathsAreKept() {
        assertThat(SafeRedirects.relativeOrRoot("/pipelines/all?q=IMPS%20Portal")).isEqualTo("/pipelines/all?q=IMPS%20Portal");
        assertThat(SafeRedirects.relativeOrRoot(null)).isEqualTo("/");
    }

    @Test
    void passwordPolicy() {
        assertThat(PasswordPolicy.violations("Short1!", "bob")).isNotEmpty();
        assertThat(PasswordPolicy.violations("alllowercaseletters", "bob")).isNotEmpty();
        assertThat(PasswordPolicy.violations("Narsing-Pass-2026", "narsing")).contains("must not contain the username");
        assertThat(PasswordPolicy.violations("Aaaaa-Strong-2026", "bob")).isNotEmpty();
        assertThat(PasswordPolicy.violations("Br4nd-New-Secret!", "bob")).isEmpty();
        assertThatThrownBy(() -> PasswordPolicy.require("weak", "bob")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ssoRolesComeFromAppRolesOrGroupsWithADefault() {
        Map<String, String> mapping = Map.of("Portal.Editor", "EDITOR", "Portal.Admin", "ADMIN", "a1b2-group-id", "ADMIN");

        assertThat(PortalOidcUserService.mapRoles(Map.of("roles", List.of("portal.editor")), mapping, "VIEWER"))
            .containsExactly(PortalRole.EDITOR);
        assertThat(PortalOidcUserService.mapRoles(Map.of("groups", List.of("a1b2-group-id")), mapping, "VIEWER"))
            .containsExactly(PortalRole.ADMIN);
        assertThat(PortalOidcUserService.mapRoles(Map.of("roles", List.of("Something.Else")), mapping, "VIEWER"))
            .containsExactly(PortalRole.VIEWER);
        assertThat(PortalOidcUserService.mapRoles(Map.of(), mapping, "")).isEmpty();
    }
}
