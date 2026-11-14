package com.euronet.eft.portal.integration.jenkins;

import com.euronet.eft.portal.common.IntegrationUnavailableException;
import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.integration.IntegrationMode;
import com.euronet.eft.portal.integration.IntegrationStatus;
import com.euronet.eft.portal.integration.IntegrationsProperties;
import com.euronet.eft.portal.integration.ToolProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Live Jenkins client using the JSON API:
 * {@code GET /job/<folder>/job/<name>/api/json?tree=builds[...]{0,N}} with basic auth (user + API token).
 * Enabled with {@code JENKINS_MODE=live}, {@code JENKINS_URL}, {@code JENKINS_USER} and {@code JENKINS_TOKEN}.
 */
@Component
@ConditionalOnProperty(prefix = "portal.integrations.jenkins", name = "mode", havingValue = "live")
public class RestJenkinsClient implements JenkinsClient {

    private static final String TREE =
            "builds[number,result,timestamp,duration,building,url,displayName,actions[causes[userName,userId]]]";

    private final RestClient http;
    private final String baseUrl;

    public RestJenkinsClient(IntegrationsProperties properties) {
        ToolProperties jenkins = properties.jenkins();
        if (jenkins.baseUrl() == null || jenkins.baseUrl().isBlank()) {
            throw new IllegalStateException("JENKINS_MODE=live needs JENKINS_URL");
        }
        this.baseUrl = jenkins.baseUrl().replaceAll("/+$", "");
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (jenkins.hasToken()) {
            String basic = Base64.getEncoder().encodeToString(
                    (jenkins.username() + ":" + jenkins.token()).getBytes(StandardCharsets.UTF_8));
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        }
        this.http = builder.build();
    }

    /** {@code GET /job/<folder>/job/<name>/<number>/consoleText}, streamed; only the tail and error lines are kept. */
    @Override
    public BuildLog log(String job, int number, int maxBytes) {
        URI target = jobUri(job).pathSegment(String.valueOf(number), "consoleText").encode().build().toUri();
        try {
            return http.get().uri(target).exchange((request, response) -> {
                if (response.getStatusCode().value() == 404) {
                    throw new NotFoundException("Jenkins build", job + " #" + number);
                }
                if (response.getStatusCode().isError()) {
                    throw new IntegrationUnavailableException("Jenkins",
                            "log of " + job + " #" + number + " returned " + response.getStatusCode(), null);
                }
                return BuildLog.read(response.getBody(), maxBytes);
            });
        } catch (RestClientException e) {
            throw new IntegrationUnavailableException("Jenkins", "log of " + job + " #" + number + ": " + e.getMessage(), e);
        }
    }

    private UriComponentsBuilder jobUri(String job) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl);
        for (String segment : job.split("/")) {
            if (!segment.isBlank()) {
                uri.pathSegment("job", segment);
            }
        }
        return uri;
    }

    @Override
    public List<JenkinsBuild> builds(String job, int limit) {
        UriComponentsBuilder uri = jobUri(job);
        URI target = uri.pathSegment("api", "json")
                .queryParam("tree", "{tree}")
                .encode()
                .buildAndExpand(TREE + "{0," + limit + "}")
                .toUri();
        try {
            JobJson body = http.get().uri(target).retrieve().body(JobJson.class);
            if (body == null || body.builds() == null) {
                return List.of();
            }
            return body.builds().stream().map(b -> toBuild(job, b)).toList();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return List.of();
            }
            throw new IntegrationUnavailableException("Jenkins", "job " + job + " returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new IntegrationUnavailableException("Jenkins", "job " + job + ": " + e.getMessage(), e);
        }
    }

    private static JenkinsBuild toBuild(String job, BuildJson b) {
        boolean building = Boolean.TRUE.equals(b.building());
        BuildResult result = BuildResult.fromJenkins(b.result(), building);
        Instant started = b.timestamp() == null ? null : Instant.ofEpochMilli(b.timestamp());
        Duration duration = b.duration() == null ? Duration.ZERO : Duration.ofMillis(b.duration());
        return new JenkinsBuild(job, b.number() == null ? 0 : b.number(), result, started, duration,
                triggeredBy(b.actions()), b.displayName(), result == BuildResult.SUCCESS, b.url());
    }

    @SuppressWarnings("unchecked")
    private static String triggeredBy(List<Map<String, Object>> actions) {
        if (actions == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (Map<String, Object> action : actions) {
            if (action != null && action.get("causes") instanceof List<?> causes) {
                for (Object cause : causes) {
                    if (cause instanceof Map<?, ?> c && c.get("userName") != null) {
                        names.add(String.valueOf(c.get("userName")));
                    }
                }
            }
        }
        return names.isEmpty() ? "SCM / timer" : names.getFirst();
    }

    @Override
    public IntegrationStatus status() {
        try {
            http.get().uri(URI.create(baseUrl + "/api/json?tree=mode")).retrieve().toBodilessEntity();
            return new IntegrationStatus("jenkins", "Jenkins", IntegrationMode.LIVE, true, baseUrl, "Reachable");
        } catch (RestClientException e) {
            return new IntegrationStatus("jenkins", "Jenkins", IntegrationMode.LIVE, false, baseUrl, e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JobJson(List<BuildJson> builds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BuildJson(Integer number, String result, Long timestamp, Long duration, Boolean building,
                     String url, String displayName, List<Map<String, Object>> actions) {
    }
}
