package com.euronet.eft.portal.ai;

import static com.euronet.eft.portal.security.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.euronet.eft.portal.inventory.importer.InventoryWorkbookFixture;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** AI features end to end in mock mode: explain a failed build, reuse it, rate it, digest, admin usage, access rules. */
@Tag("db")
@SpringBootTest(properties = {"portal.ai.mode=mock", "portal.ai.digest.enabled=false"})
@AutoConfigureMockMvc
class AiIntegrationTest {

    private static final RequestPostProcessor VIEWER = user("ai-viewer").roles("VIEWER");
    private static final RequestPostProcessor EDITOR = user("ai-editor").roles("EDITOR");
    private static final RequestPostProcessor ADMIN = user("ai-admin").roles("ADMIN");

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void inventory() throws Exception {
        mvc.perform(multipart("/api/inventory/import").file(new MockMultipartFile("file", "inventory.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", InventoryWorkbookFixture.twoOwners()))
                        .with(EDITOR).with(xsrf()))
                .andExpect(status().isOk());
    }

    @Test
    void statusTellsTheUiWhatIsOn() throws Exception {
        mvc.perform(get("/api/ai/status").with(VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.mode").value("MOCK"));
    }

    @Test
    void anyoneCanExplainAFailedBuildOnceAndRateIt() throws Exception {
        Build failed = findBuild("FAILURE");
        String base = "/api/ai/builds/" + failed.appId() + "/" + failed.env() + "/" + failed.number();

        mvc.perform(get(base).with(VIEWER)).andExpect(status().isNoContent());

        String body = mvc.perform(post(base + "/explain").with(VIEWER).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.mode").value("MOCK"))
                .andExpect(jsonPath("$.result.content.category").isNotEmpty())
                .andExpect(jsonPath("$.result.content.fixSteps").isArray())
                .andExpect(jsonPath("$.result.masking").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.result.id");

        // second request reuses the stored answer (same id, no new AI call)
        mvc.perform(post(base + "/explain").with(EDITOR).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(id));
        mvc.perform(get(base).with(VIEWER)).andExpect(status().isOk()).andExpect(jsonPath("$.result.id").value(id));

        // viewers may not pay for a fresh answer; editors may
        mvc.perform(post(base + "/explain").param("refresh", "true").with(VIEWER).with(xsrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/explain").param("refresh", "true").with(EDITOR).with(xsrf()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/ai/insights/" + id + "/feedback").with(VIEWER).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpful").value(1))
                .andExpect(jsonPath("$.myVote").value(1));
    }

    @Test
    void successfulBuildsAreNotExplained() throws Exception {
        Build ok = findBuild("SUCCESS");
        mvc.perform(post("/api/ai/builds/" + ok.appId() + "/" + ok.env() + "/" + ok.number() + "/explain").with(VIEWER).with(xsrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explainingNeedsTheCsrfHeader() throws Exception {
        Build failed = findBuild("FAILURE");
        mvc.perform(post("/api/ai/builds/" + failed.appId() + "/" + failed.env() + "/" + failed.number() + "/explain").with(VIEWER))
                .andExpect(status().isForbidden());
    }

    @Test
    void digestOnDemandIsForAdministratorsAndEveryoneCanReadIt() throws Exception {
        mvc.perform(post("/api/ai/digest").with(EDITOR).with(xsrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/digest").with(ADMIN).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.headline").isNotEmpty())
                .andExpect(jsonPath("$.content.overall").isNotEmpty())
                .andExpect(jsonPath("$.content.areas").isArray());
        mvc.perform(get("/api/ai/digest/latest").with(VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feature").value("executive-digest"));
    }

    @Test
    void usageIsVisibleToAdministratorsOnly() throws Exception {
        mvc.perform(post("/api/ai/digest").with(ADMIN).with(xsrf())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/ai").with(VIEWER)).andExpect(status().isForbidden());
        String body = mvc.perform(get("/api/admin/ai").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.mode").value("MOCK"))
                .andReturn().getResponse().getContentAsString();
        Integer calls = JsonPath.read(body, "$.usage.callsThisMonth");
        assertThat(calls).isPositive();
    }

    private record Build(long appId, String env, int number) {
    }

    /** A build with the given result from any pipeline of the imported inventory (mock Jenkins). */
    private Build findBuild(String result) throws Exception {
        String metrics = mvc.perform(get("/api/pipelines/metrics").with(VIEWER)).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> rows = JsonPath.read(metrics, "$[*]");
        for (Map<String, Object> row : rows) {
            long appId = ((Number) row.get("applicationId")).longValue();
            String env = (String) row.get("environment");
            String builds = mvc.perform(get("/api/pipelines/" + appId + "/" + env + "/builds").param("limit", "200").with(VIEWER))
                    .andReturn().getResponse().getContentAsString();
            List<Integer> numbers = JsonPath.read(builds, "$[?(@.result == '" + result + "')].number");
            if (!numbers.isEmpty()) {
                return new Build(appId, env, numbers.getFirst());
            }
        }
        throw new AssertionError("no " + result + " build in the mock data");
    }
}
