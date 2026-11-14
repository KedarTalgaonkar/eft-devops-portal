package com.euronet.eft.portal.pipelines;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineMetric;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PipelineController.class)
@WithMockUser(roles = "VIEWER")
class PipelineControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PipelineService service;

    @MockitoBean
    private PipelineInsightsService insights;

    @Test
    void metricsUseEnvironmentAndDays() throws Exception {
        when(service.metrics(eq("SIT"), eq(7))).thenReturn(List.of(
                new PipelineMetric(1L, "Prepaid QR", "CBC", "Samar", "SIT", "prepaid-qr-SIT",
                        10, 7, 2, 1, 0, 0, 70.0, null, null)));

        mvc.perform(get("/api/pipelines/metrics").param("env", "SIT").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].project").value("Prepaid QR"))
                .andExpect(jsonPath("$[0].environment").value("SIT"))
                .andExpect(jsonPath("$[0].totalBuilds").value(10))
                .andExpect(jsonPath("$[0].aborted").value(1));
    }

    @Test
    void environmentIsOptional() throws Exception {
        when(service.metrics(isNull(), eq(30))).thenReturn(List.of());

        mvc.perform(get("/api/pipelines/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unknownEnvironmentIsBadRequest() throws Exception {
        mvc.perform(get("/api/pipelines/metrics").param("env", "!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid environment: !!!"));
    }

    @Test
    void anyEnvironmentCodeIsAcceptedAndNormalised() throws Exception {
        when(service.metrics(eq("PREPROD"), eq(30))).thenReturn(List.of());

        mvc.perform(get("/api/pipelines/metrics").param("env", "pre-prod"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(service).metrics("PREPROD", 30);
    }

    @Test
    void unknownPipelineIsNotFound() throws Exception {
        when(service.builds(99L, "UAT", 50)).thenThrow(new NotFoundException("Pipeline", "99/UAT"));

        mvc.perform(get("/api/pipelines/99/UAT/builds"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pipeline not found: 99/UAT"));
    }
}
