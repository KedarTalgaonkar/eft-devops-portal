package com.euronet.eft.portal.announcement;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.euronet.eft.portal.security.Xsrf.xsrf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Header announcements: who may publish, the time window, ending early, validation.
 */
@Tag("db")
@SpringBootTest
@AutoConfigureMockMvc
class AnnouncementIntegrationTest {

    private static final RequestPostProcessor VIEWER = user("an-viewer").roles("VIEWER");
    private static final RequestPostProcessor EDITOR = user("an-editor").roles("EDITOR");

    @Autowired
    private MockMvc mvc;

    private static String body(String kind, String message, Instant start, Instant end, String link) {
        return "{\"kind\":\"" + kind + "\",\"message\":\"" + message + "\",\"startsAt\":" + (start == null ? "null" : "\"" + start + "\"")
            + ",\"endsAt\":\"" + end + "\",\"linkUrl\":" + (link == null ? "null" : "\"" + link + "\"") + "}";
    }

    @Test
    void viewersReadEditorsPublishAndTheWindowIsRespected() throws Exception {
        Instant now = Instant.now();
        String live = "UAT deployment " + UUID.randomUUID();
        String later = "Release window " + UUID.randomUUID();

        mvc.perform(post("/api/announcements").with(VIEWER).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("RELEASE", live, null, now.plus(2, ChronoUnit.HOURS), null)))
            .andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/announcements").with(EDITOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("RELEASE", live, null, now.plus(2, ChronoUnit.HOURS), "/pipelines/deployments")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.linkLabel").value("Details"))
            .andExpect(jsonPath("$.createdBy").value("an-editor"))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/announcements").with(EDITOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("MAINTENANCE", later, now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS), null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mvc.perform(get("/api/announcements/active").with(VIEWER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].message", hasItem(live)))
            .andExpect(jsonPath("$[*].message", not(hasItem(later))));

        Integer id = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/announcements/" + id + "/end").with(EDITOR).with(xsrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ENDED"));
        mvc.perform(get("/api/announcements/active").with(VIEWER))
            .andExpect(jsonPath("$[*].message", not(hasItem(live))));
        mvc.perform(get("/api/announcements").with(VIEWER))
            .andExpect(jsonPath("$[*].message", hasItem(later)));
    }

    @Test
    void rejectsBadInput() throws Exception {
        Instant now = Instant.now();
        // unsafe link
        mvc.perform(post("/api/announcements").with(EDITOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("INFO", "Hello all", null, now.plus(1, ChronoUnit.HOURS), "javascript:alert(1)")))
            .andExpect(status().isBadRequest());
        // ends before it starts
        mvc.perform(post("/api/announcements").with(EDITOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("INFO", "Hello all", now.plus(2, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), null)))
            .andExpect(status().isBadRequest());
        // longer than 30 days
        mvc.perform(post("/api/announcements").with(EDITOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("INFO", "Hello all", null, now.plus(40, ChronoUnit.DAYS), null)))
            .andExpect(status().isBadRequest());
        // needs CSRF
        mvc.perform(post("/api/announcements").with(EDITOR).contentType(MediaType.APPLICATION_JSON)
                .content(body("INFO", "Hello all", null, now.plus(1, ChronoUnit.HOURS), null)))
            .andExpect(status().isForbidden());
    }
}
