# Architecture

```
 Browser ──► frontend (nginx :8081) ──/api──► backend (Spring Boot :8080) ──► PostgreSQL
                Angular 22 SPA                    │
                                                   ├─► Jenkins       (JenkinsClient: mock | live)
                                                   ├─► GitHub        (GithubClient: mock)
                                                   ├─► SonarQube     (SonarClient: mock)
                                                   ├─► Artifactory   (ArtifactoryClient: mock)
                                                   ├─► Grafana/Prom. (MonitoringClient: mock)
                                                   ├─► SBOM scanner  (SbomScanner: mock)
                                                   └─► Tomcat URLs   (ServiceHealthService: mock | live probes)
```

## Backend (`backend/`, Java 25, Spring Boot 4.1)

Code is organised by feature under `com.euronet.eft.portal`:

| Package | Role |
|---|---|
| `common` | `Environment` enum (DEVELOP, SIT, UAT), error handling (`ApiError`, `GlobalExceptionHandler`), helpers |
| `config` | CORS, OpenAPI. Security (Entra ID) will be added here in phase 4 |
| `integration.*` | One sub-package per external tool: a **port interface**, a **mock** and (for Jenkins) a **live** client |
| `inventory` | Applications and their Tomcat deployments per environment (PostgreSQL), Excel import |
| `pipelines` | Jenkins build counts per project × environment, deployment history |
| `health` | Scheduled HTTP probes of every deployment URL |
| `dashboard` | Overview KPIs, environment status grid, integration strip |
| `github`, `quality`, `artifacts`, `monitoring`, `sbom` | REST controllers over the matching integration (SBOM also persists scans) |

### Data
* **Persisted** (Flyway, `db/migration`): `application`, `deployment`, `sbom_scan`, `sbom_vulnerability`.
* **Fetched live and cached** (Caffeine, 60 s): Jenkins builds and other tool data.
* `db/dev/R__dev_sample_data.sql` is applied only with the `dev` profile and uses fake `*.example.internal` hosts.
  The real inventory (with internal IPs) is loaded through `POST /api/inventory/import` and never committed.

### How projects map to Jenkins jobs
A *pipeline* is one application in one environment. Its Jenkins job is the `jenkinsJob` set on any of its
deployments (editable on the Inventory page), otherwise `JENKINS_JOB_PATTERN` (default `{app}-{ENV}`,
e.g. `prepaid-qr-SIT`). Placeholders: `{app}`, `{client}` (slugified), `{ENV}`, `{env}`.

### Integration modes
Every client bean is chosen with `@ConditionalOnProperty(portal.integrations.<tool>.mode)`; `mock` is the default,
so the stack runs with no tokens. Switching one tool to `live` needs its URL and token in `.env`
(see [integrations.md](integrations.md)). Use lower-case `mock` / `live`.

## Frontend (`frontend/`, Angular 22)
* Standalone components, signals, lazy-loaded feature routes (`src/app/features/*`).
* `core/` holds the shell (sidebar + toolbar), the API base URL and the HTTP error interceptor.
* `shared/` holds reusable UI: status badges, environment chips, KPI cards, page header, state panels.
* Every call goes to the relative `/api` path: nginx proxies it in the container, `proxy.conf.json` in `ng serve`.

## Containers
| File | Purpose |
|---|---|
| `docker-compose.yml` | postgres + backend + frontend, the prod-like stack |
| `compose.dev.yml` | override: `ng serve` with live reload, backend run from mounted source |
| `compose.test.yml` | backend `mvn verify` against a throwaway postgres, frontend `ng test` |
| `backend/Dockerfile` | Maven on Temurin 25 → layered jar on Temurin 25 JRE (alpine), non-root, healthcheck |
| `frontend/Dockerfile` | Node 24 build → nginx 1.29 serving the SPA and proxying `/api` |
