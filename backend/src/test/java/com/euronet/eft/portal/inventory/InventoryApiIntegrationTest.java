package com.euronet.eft.portal.inventory;

import com.euronet.eft.portal.inventory.importer.InventoryWorkbookFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static com.euronet.eft.portal.security.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Excel import through the REST API into PostgreSQL, then read back through inventory and pipelines.
 */
@Tag("db")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "it-editor", roles = "EDITOR")
class InventoryApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DeploymentRepository deployments;

    @Autowired
    private ApplicationRepository applications;

    private static MockMultipartFile xlsx(byte[] content) {
        return new MockMultipartFile("file", "inventory.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    }

    @Test
    void importIsIdempotentAndFeedsPipelines() throws Exception {
        MockMultipartFile file = xlsx(InventoryWorkbookFixture.twoOwners());

        mvc.perform(multipart("/api/inventory/import").file(file).with(xsrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sheetsRead").value(2))
            .andExpect(jsonPath("$.rowsRead").value(5));

        // second import changes nothing
        mvc.perform(multipart("/api/inventory/import").file(file).with(xsrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicationsCreated").value(0))
            .andExpect(jsonPath("$.deploymentsCreated").value(0))
            .andExpect(jsonPath("$.deploymentsUpdated").value(0));

        mvc.perform(get("/api/inventory/applications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", hasItem("QR ENGINE")))
            .andExpect(jsonPath("$[*].name", hasItem("ACS 2.0")));

        mvc.perform(get("/api/pipelines/metrics").param("env", "UAT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].jenkinsJob", hasItem("qr-engine-UAT")));
    }

    @Test
    void jenkinsJobCanBeOverriddenAndTheEditorIsRecorded() throws Exception {
        mvc.perform(multipart("/api/inventory/import").file(xlsx(InventoryWorkbookFixture.twoOwners())).with(xsrf()))
            .andExpect(status().isOk());

        Deployment acs = deployments.findAllWithApplication().stream()
            .filter(d -> d.getApplication().getName().equals("ACS 2.0") && d.getEnvironment().equals("UAT"))
            .findFirst()
            .orElseThrow();
        assertThat(acs.getJavaVersion()).isEqualTo("8u321");

        mvc.perform(put("/api/inventory/deployments/{id}/jenkins-job", acs.getId()).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jenkinsJob\":\"EFT/acs/uat-deploy\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effectiveJenkinsJob").value("EFT/acs/uat-deploy"));

        mvc.perform(get("/api/inventory/changes").param("applicationId", String.valueOf(acs.getApplication().getId())))
            .andExpect(jsonPath("$[?(@.field == 'jenkinsJob')].changedBy", hasItem("it-editor")));
    }

    @Test
    void newEnvironmentsOwnerMovesAndRepositoriesNeedNoCodeChange() throws Exception {
        mvc.perform(multipart("/api/inventory/import").with(xsrf()).file(xlsx(InventoryWorkbookFixture.scalable("Ganesh",
                "http://preprod-recon.example.internal:8080"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newEnvironments", hasItem("PERF")))
            .andExpect(jsonPath("$.newEnvironments", hasItem("PREPROD")));

        // the team hands the application over and the pre-prod URL moves
        mvc.perform(multipart("/api/inventory/import").with(xsrf()).file(xlsx(InventoryWorkbookFixture.scalable("Deepak",
                "http://preprod2-recon.example.internal:8080"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newEnvironments").isEmpty())
            .andExpect(jsonPath("$.applicationsUpdated").value(1))
            .andExpect(jsonPath("$.deploymentsUpdated").value(1));

        mvc.perform(get("/api/environments"))
            .andExpect(jsonPath("$[*].code", hasItem("PERF")))
            .andExpect(jsonPath("$[*].code", hasItem("PREPROD")));

        long id = applications.findByNameAndClient("Recon Engine", "Euronet").orElseThrow().getId();
        mvc.perform(get("/api/inventory/applications/{id}", id))
            .andExpect(jsonPath("$.owner").value("Deepak"))
            .andExpect(jsonPath("$.repositories", hasItem("eft-org/recon-common")));
        mvc.perform(get("/api/inventory/changes").param("applicationId", String.valueOf(id)))
            .andExpect(jsonPath("$[?(@.field == 'owner')].newValue", hasItem("Deepak")))
            .andExpect(jsonPath("$[?(@.field == 'baseUrl')].newValue", hasItem("http://preprod2-recon.example.internal:8080")));
        mvc.perform(get("/api/pipelines/metrics").param("env", "perf"))
            .andExpect(jsonPath("$[*].jenkinsJob", hasItem("recon-engine-PERF")));
    }

    @Test
    void applicationsAndPathsCanBeAddedEditedAndRemovedFromThePortal() throws Exception {
        String name = "UI App " + java.util.UUID.randomUUID().toString().substring(0, 8);
        String app = "{\"name\":\"" + name + "\",\"client\":\"Test Bank\",\"owner\":\"Asha\",\"repositories\":[\"eft-org/ui-app\"]}";
        String created = mvc.perform(post("/api/inventory/applications").with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(app))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.owner").value("Asha"))
            .andExpect(jsonPath("$.repositories", hasItem("eft-org/ui-app")))
            .andReturn().getResponse().getContentAsString();
        long appId = Long.parseLong(created.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

        // same name + client again is refused
        mvc.perform(post("/api/inventory/applications").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(app))
            .andExpect(status().isBadRequest());

        mvc.perform(put("/api/inventory/applications/{id}", appId).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"client\":\"Test Bank\",\"owner\":\"Ravi\",\"repositories\":[\"eft-org/ui-app\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.owner").value("Ravi"));

        String path = "{\"environment\":\"sit\",\"contextName\":\"UIAPP\",\"baseUrl\":\"http://sit-ui.example.internal:8080/\","
            + "\"tomcatVersion\":\"9.0.85\",\"javaVersion\":\"17\",\"tomcatAdminDone\":true,\"buildXmlDone\":false}";
        String dep = mvc.perform(post("/api/inventory/applications/{id}/deployments", appId).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(path))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.environment").value("SIT"))
            .andExpect(jsonPath("$.platform").value("TOMCAT"))
            .andExpect(jsonPath("$.contextUrl").value("http://sit-ui.example.internal:8080/UIAPP/"))
            .andReturn().getResponse().getContentAsString();
        long depId = Long.parseLong(dep.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

        // duplicate path, unknown environment and a bad URL are refused
        mvc.perform(post("/api/inventory/applications/{id}/deployments", appId).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(path))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventory/applications/{id}/deployments", appId).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"environment\":\"MARS\",\"contextName\":\"X\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventory/applications/{id}/deployments", appId).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"environment\":\"UAT\",\"baseUrl\":\"javascript:alert(1)\"}"))
            .andExpect(status().isBadRequest());

        mvc.perform(put("/api/inventory/deployments/{id}", depId).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(path.replace("\"buildXmlDone\":false", "\"buildXmlDone\":true").replace("\"17\"", "\"21\"")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.javaVersion").value("21"))
            .andExpect(jsonPath("$.buildXmlDone").value(true));

        mvc.perform(delete("/api/inventory/deployments/{id}", depId).with(xsrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/inventory/applications/{id}", appId))
            .andExpect(jsonPath("$.deployments").isEmpty());

        mvc.perform(get("/api/inventory/changes").param("applicationId", String.valueOf(appId)))
            .andExpect(jsonPath("$[?(@.field == 'owner')].newValue", hasItem("Ravi")))
            .andExpect(jsonPath("$[?(@.field == 'javaVersion')].newValue", hasItem("21")))
            .andExpect(jsonPath("$[*].changeType", hasItem("DEPLOYMENT_REMOVED")))
            .andExpect(jsonPath("$[*].changedBy", hasItem("it-editor")));
    }

    @Test
    @WithMockUser(username = "it-viewer", roles = "VIEWER")
    void viewersCannotEditTheInventory() throws Exception {
        mvc.perform(post("/api/inventory/applications").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsNonExcelUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "x".getBytes());
        mvc.perform(multipart("/api/inventory/import").file(file).with(xsrf()))
            .andExpect(status().isBadRequest());
    }
}
