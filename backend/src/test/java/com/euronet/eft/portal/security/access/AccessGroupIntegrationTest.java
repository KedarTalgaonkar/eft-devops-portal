package com.euronet.eft.portal.security.access;

import com.euronet.eft.portal.security.access.ModuleAccessService.GroupRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.euronet.eft.portal.security.Xsrf.xsrf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group-based module access end to end: configuration through the admin API, enforcement on module APIs,
 * the role cap, the session view and the filtered Command centre.
 */
@Tag("db")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "it-admin", roles = "ADMIN")
class AccessGroupIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ModuleAccessService access;

    private long everyoneId() {
        return access.list().stream().filter(ModuleAccessService.GroupView::builtin).findFirst().orElseThrow().id();
    }

    private static String body(String name, Map<PortalModule, AccessLevel> modules, List<String> sso, List<String> members) {
        StringBuilder m = new StringBuilder();
        modules.forEach((k, v) -> m.append(m.isEmpty() ? "" : ",").append('"').append(k).append("\":\"").append(v).append('"'));
        return "{\"name\":\"" + name + "\",\"modules\":{" + m + "},\"ssoGroups\":" + json(sso) + ",\"members\":" + json(members) + "}";
    }

    private static String json(List<String> values) {
        return values.stream().map(v -> "\"" + v + "\"").reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }

    private static Map<PortalModule, AccessLevel> all(AccessLevel level) {
        Map<PortalModule, AccessLevel> m = new EnumMap<>(PortalModule.class);
        for (PortalModule p : PortalModule.values()) {
            m.put(p, level);
        }
        return m;
    }

    @AfterEach
    void restoreDefaults() {
        access.list().stream().filter(g -> !g.builtin()).forEach(g -> access.delete(g.id()));
        access.update(everyoneId(), new GroupRequest(ModuleAccessService.EVERYONE, "Applies to every signed-in user.",
            all(AccessLevel.EDIT), List.of(), List.of()));
    }

    @Test
    void defaultsKeepTodaysBehaviourWithRolesAsTheCeiling() throws Exception {
        mvc.perform(get("/api/pipelines/metrics").with(user("qa-1").roles("VIEWER"))).andExpect(status().isOk());
        // a viewer never edits, whatever the groups grant
        mvc.perform(post("/api/inventory/applications").with(user("qa-1").roles("VIEWER")).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/session").with(user("qa-1").roles("VIEWER")))
            .andExpect(jsonPath("$.modules.PIPELINES").value("VIEW"))
            .andExpect(jsonPath("$.modules.INVENTORY").value("VIEW"));
    }

    @Test
    void groupsHideModulesAndGrantThemBackToMembers() throws Exception {
        Map<PortalModule, AccessLevel> everyone = all(AccessLevel.EDIT);
        everyone.put(PortalModule.PIPELINES, AccessLevel.NONE);
        mvc.perform(put("/api/admin/access-groups/{id}", everyoneId()).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("Everyone", everyone, List.of(), List.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules.PIPELINES").value("NONE"));

        mvc.perform(get("/api/pipelines/metrics").with(user("qa-1").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/builds/1/SIT/1").with(user("qa-1").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/attention").with(user("qa-1").roles("VIEWER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.areas[*].key", not(hasItem("delivery"))))
            .andExpect(jsonPath("$.items[*].area", not(hasItem("Delivery"))));
        mvc.perform(get("/api/dashboard/overview").with(user("qa-1").roles("VIEWER")))
            .andExpect(jsonPath("$.pipelines").doesNotExist());

        // the release team gets Pipelines back, by username (local accounts) or by SSO group
        mvc.perform(post("/api/admin/access-groups").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("Release team", Map.of(PortalModule.PIPELINES, AccessLevel.EDIT), List.of("EFT-Delivery"), List.of("Rel-1"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.members", hasItem("Rel-1")));
        mvc.perform(get("/api/pipelines/metrics").with(user("rel-1").roles("EDITOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/pipelines/metrics").with(user("qa-1").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/attention").with(user("rel-1").roles("EDITOR")))
            .andExpect(jsonPath("$.areas[*].key", hasItem("delivery")));

        // the preview shows what a user would get and why
        mvc.perform(post("/api/admin/access-groups/preview").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"someone\",\"role\":\"VIEWER\",\"ssoGroups\":[\"eft-delivery\"]}"))
            .andExpect(jsonPath("$.modules.PIPELINES").value("VIEW"))
            .andExpect(jsonPath("$.groups", hasItem("Release team")));

        // administrators are never locked out
        mvc.perform(get("/api/pipelines/metrics")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit"))
            .andExpect(jsonPath("$[*].action", hasItem("ACCESS_GROUP_CREATED")));
    }

    @Test
    void everyoneCannotBeDeletedAndNamesAreUnique() throws Exception {
        mvc.perform(delete("/api/admin/access-groups/{id}", everyoneId()).with(xsrf())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/access-groups").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("everyone", Map.of(), List.of(), List.of())))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/access-groups").with(user("ed").roles("EDITOR"))).andExpect(status().isForbidden());
    }
}
