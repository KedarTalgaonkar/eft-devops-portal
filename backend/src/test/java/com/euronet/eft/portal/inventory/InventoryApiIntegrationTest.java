package com.euronet.eft.portal.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static com.euronet.eft.portal.security.Xsrf.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Excel import through the REST API into PostgreSQL, then read back through inventory and pipelines. */
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

        mvc.perform(put("/api/inventory/deployments/{id}", acs.getId()).with(xsrf())
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
    void rejectsNonExcelUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "x".getBytes());
        mvc.perform(multipart("/api/inventory/import").file(file).with(xsrf()))
                .andExpect(status().isBadRequest());
    }

    private static MockMultipartFile xlsx(byte[] content) {
        return new MockMultipartFile("file", "inventory.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    }
}
