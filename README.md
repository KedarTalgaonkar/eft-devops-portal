# EFT DevOps Portal · Euronet India

One portal for the EFT applications. Every module has an **Overview** tab for leadership (RAG status, KPIs against the
previous period, trends, top issues) and **detail tabs** for support teams (full tables, filters, identifiers, deep
links).

| Module                     | Overview (executive)                                                                         | Detail tabs (support)                                                               | Data (phase 1)               |
|----------------------------|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|------------------------------|
| **Command centre**         | Overall RAG, one scorecard per area, top issues, footprint per environment                   | Executive digest (AI) · Issues · Environment status · Service health · Integrations | all modules                  |
| **Pipelines**              | Build success, deployments/day, MTTR, failing pipelines, daily trend, per-environment health | Pipeline metrics (project × environment table) · Deployment history                 | Jenkins (mock or **live**)   |
| **Source code**            | Teams, PR ageing, review bottlenecks                                                         | Repositories + commit activity · Pull requests                                      | mock                         |
| **Artifacts**              | Storage and repository mix                                                                   | Browse repositories and artifacts                                                   | mock                         |
| **Code quality**           | Gate pass rate, coverage, riskiest projects                                                  | Projects                                                                            | mock                         |
| **SBOM & vulnerabilities** | Exposure by severity, riskiest projects, fix-first list                                      | Scans & findings                                                                    | mock scanner, results stored |
| **Monitoring**             | Firing alerts, hosts near capacity, Grafana links                                            | Alerts · Infrastructure                                                             | mock                         |
| **Application inventory**  | Estate size, Java/Tomcat currency, setup completeness                                        | Deployment paths · Change log · Environments                                        | PostgreSQL                   |

## Built to grow without code changes

| Change                                         | What happens                                                                                                                                                                                                            |
|------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| New environment (PERF, PREPROD, PROD, DR, ...) | Add its column group to the workbook and import: it is registered in the `environment` table and appears in every filter, grid and chart. Rename, reorder, mark as production or hide it on *Inventory › Environments*. |
| New application, client or deployment path     | Add a row or a context in the workbook and import; pipelines, health probes and dashboards pick it up.                                                                                                                  |
| Owner hand-over                                | Move the row to the new owner's sheet (or use an `Owner` column) and import: the owner changes and the move is logged on *Inventory › Change log*.                                                                      |
| More repositories                              | Optional `Repository` column (comma separated) or edit on *Inventory › Deployment paths*; live GitHub lists every team and repo it can see.                                                                             |
| Same application for several clients           | Default Jenkins job names include the client automatically (`imps-portal-cosmos-bank-SIT`) so jobs never collide.                                                                                                       |
| New data source                                | Follow the port + mock + live pattern in [docs/adding-a-module.md](docs/adding-a-module.md).                                                                                                                            |

## Branding

Euronet India theme (navy, teal, saffron accent, IBM Plex). Tool logos come from the open-source `simple-icons` set and
are bundled locally. To show the official Euronet logo,
see [frontend/public/brand/README.md](frontend/public/brand/README.md).

Language: **Indian English** (`en-IN`): British spelling, dates like 14 Nov 2026, Indian digit grouping (1,00,000).
AI answers are asked to use the same.

Stack: **Angular 22** + **Spring Boot 4.1 on Java 25** + **PostgreSQL 17**, all running in Docker.
Nothing besides Docker needs to be installed: Java, Maven, Node and the Angular CLI run in containers.

## Run it

Prerequisites: Docker with Compose (Rancher Desktop or Docker Desktop) running, and git.

```bash
cp .env.example .env          # optional: every value has a default
docker compose up --build -d
docker compose ps             # wait until backend and frontend are "healthy"
```

| URL                                   | What                                                  |
|---------------------------------------|-------------------------------------------------------|
| http://localhost:8081                 | the portal (sign-in required)                         |
| http://localhost:8180                 | local Keycloak, the development stand-in for Entra ID |
| http://localhost:8081/swagger-ui.html | REST API (Swagger UI, administrators)                 |
| http://localhost:8080/actuator/health | backend health                                        |

The `dev` profile (default) loads a small sample inventory with fake hosts, so every page has data.

### Sign in

* **SSO:** click *Sign in with SSO*. Locally this is Keycloak with test users `viewer`, `editor` and `admin`
  (passwords in [docs/security.md](docs/security.md#local-keycloak-development-only)). In production it is Microsoft
  Entra ID.
* **Portal account:** *Use a local account instead*. There is no default password: create the first administrator by
  setting `PORTAL_BOOTSTRAP_ADMIN_USERNAME` / `PORTAL_BOOTSTRAP_ADMIN_PASSWORD` in `.env` before the first start.
  The password must be changed at first sign-in; then remove it from `.env`. Further accounts: **Administration →
  Users**.

Roles: Viewer (read), Editor (import and edit), Admin (environments, users, audit log). Details, Entra ID setup and all
security controls: [docs/security.md](docs/security.md).

### Load the real inventory

Sign in as an Editor or Admin and upload the *Jenkins Automation - Tomcat Based Projects* workbook with **Import Excel**
on **Application inventory → Deployment paths**.

Each sheet is an owner; each row's SIT and UAT blocks become deployments. Importing again updates the rows and never
deletes anything. Keep the real workbook out of git (it holds internal IPs; `*.xlsx` is git-ignored).

### Switch an integration to live data

Set `<TOOL>_MODE=live` plus its URL and token in `.env`, then `docker compose up -d --build backend`.
Jenkins has a working live client today; see [docs/integrations.md](docs/integrations.md).

### AI features

* **Build failure explainer:** *Pipelines → All pipelines*, open a project, **Explain** on a failed build: likely cause,
  evidence lines from the log, fix steps, who should act. Stored and reused, so each build is analysed once.
* **Executive digest:** its own tab, *Command centre → Executive digest*, written every weekday morning (and on demand
  by admins), with earlier digests alongside.
* **Administration → AI usage:** mode, masking, spend against the monthly budget, every request.

The Docker stack runs them in **mock** mode (sample answers, nothing leaves the machine). To use Claude set
`AI_MODE=live`, `AI_PROVIDER` and its credentials. Data sent, masking, limits and costs: [docs/ai.md](docs/ai.md).

## Develop

```bash
docker compose -f docker-compose.yml -f compose.dev.yml up
```

* Frontend at http://localhost:4200 with live reload (source mounted, `ng serve --poll`).
* Backend runs `mvn spring-boot:run` from the mounted source; after Java changes run
  `docker compose -f docker-compose.yml -f compose.dev.yml restart backend`.

Angular CLI commands, run in a container:

```bash
docker run --rm -it -v "$PWD/frontend:/app" -w /app node:24-alpine npx ng generate component features/x/x-page
```

## Test

```bash
docker compose -f compose.test.yml run --rm backend-test    # unit + PostgreSQL integration tests
docker compose -f compose.test.yml run --rm frontend-test   # Angular unit tests
docker compose -f compose.test.yml down -v
```

The backend image build also runs the unit tests (tests tagged `db` run only in `compose.test.yml`).

## Security scan (OWASP ZAP baseline)

```bash
docker compose -f compose.security.yml run --rm zap
```

Latest result (with sign-in enabled): 0 failures, 60 passes, 1 accepted warning. Details, controls and open
items: [docs/security.md](docs/security.md).

## Behind a corporate proxy or mirror

If Maven Central or npmjs is blocked inside containers, set `MAVEN_MIRROR_URL` and/or `NPM_REGISTRY` in `.env`
(for example your Artifactory virtual repositories) and rebuild.

## Layout

```
backend/    Spring Boot API         (docs/architecture.md)
frontend/   Angular SPA
docs/       architecture, integrations, how to add a module
docker-compose.yml  compose.dev.yml  compose.test.yml  .env.example
```

## Roadmap

* Phase 2: live GitHub and SonarQube clients.
* Phase 3: live Artifactory, Grafana/Prometheus and SBOM (CycloneDX + Trivy/Dependency-Track); stored history for
  trends.
* Phase 4: images in Artifactory, OpenShift deployment, CI for this repo, authenticated ZAP scan. (Sign-in with Entra
  ID / portal accounts and roles: done.)
