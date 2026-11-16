# Integrations

All tools start in `mock` mode. Set the variables below in `.env` (copied from `.env.example`) and run
`docker compose up -d --build backend` to switch one to `live`.

| Tool                 | Status                | Variables                                                                                               | API the live client calls                                                                                                                                                               |
|----------------------|-----------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Jenkins              | **live client ready** | `JENKINS_MODE=live`, `JENKINS_URL`, `JENKINS_USER`, `JENKINS_TOKEN` (API token from *User → Configure*) | `GET /job/<folder>/job/<name>/api/json?tree=builds[number,result,timestamp,duration,building,url,displayName,actions[causes[userName]]]{0,200}`                                         |
| GitHub               | mock (phase 2)        | `GITHUB_MODE`, `GITHUB_URL`, `GITHUB_TOKEN` (fine-grained, read-only)                                   | `GET /orgs/{org}/teams`, `/teams/{team}/repos`, `/repos/{o}/{r}/commits?since=`, `/search/issues?q=is:pr+org:{org}`                                                                     |
| SonarQube            | mock (phase 2)        | `SONAR_MODE`, `SONAR_URL`, `SONAR_TOKEN`                                                                | `GET /api/projects/search`, `/api/qualitygates/project_status?projectKey=`, `/api/measures/component?metricKeys=bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density,...` |
| Artifactory          | mock (phase 3)        | `ARTIFACTORY_MODE`, `ARTIFACTORY_URL`, `ARTIFACTORY_TOKEN`                                              | `GET /api/repositories`, `/api/storageinfo`, `POST /api/search/aql`                                                                                                                     |
| Grafana & Prometheus | mock (phase 3)        | `MONITORING_MODE`, `GRAFANA_URL`, `GRAFANA_TOKEN`, `PROMETHEUS_URL`                                     | Grafana `GET /api/search?type=dash-db`, `/api/alertmanager/grafana/api/v2/alerts`; Prometheus `GET /api/v1/targets`, `/api/v1/query`                                                    |
| SBOM                 | mock (phase 3)        | `SBOM_MODE`                                                                                             | CycloneDX generation in the build (cyclonedx-maven-plugin), scan with Trivy (`trivy sbom`) or upload to Dependency-Track (`PUT /api/v1/bom`)                                            |
| Service health       | mock / live           | `HEALTH_PROBE_MODE=live`                                                                                | `GET <deployment URL>/<context>/` every 2 minutes; 2xx, 3xx, 401 and 403 count as UP                                                                                                    |

Notes

* Jenkins job names map to projects as described in [architecture.md](architecture.md#how-projects-map-to-jenkins-jobs).
* Live health probes only make sense where the backend container can reach the Tomcat hosts (inside the corporate
  network).
  HTTPS hosts with internal certificates need the CA added to the backend image's truststore.
* Tokens are read from the environment only; never commit `.env`.
