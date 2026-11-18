# EFT DevOps Portal – air-gapped installation

This bundle runs the portal on a server with **no internet access**. It contains every container image needed, so nothing is built or downloaded on the server.

| Container | Image | Role |
|---|---|---|
| frontend | `eft-devops-portal-frontend:<version>` | nginx: serves the web app and proxies `/api` |
| backend | `eft-devops-portal-backend:<version>` | Spring Boot API on Java 25 |
| postgres | `postgres:17-alpine` | Database (data kept in the `pgdata` volume) |

Images are built for **linux/amd64**.

## Server requirements

- Linux x86-64 with Docker Engine 24+ and the Docker Compose v2 plugin (install both from your internal package mirror; they are not in this bundle).
- 2 vCPU, 4 GB RAM and 10 GB free disk space as a minimum.
- One inbound port for users (8081 by default).

## 1. Build the bundle (machine with internet)

From the repository root:

```bash
bash deploy/airgap/build-bundle.sh
```

This produces `dist/eft-portal-airgap-<version>.tar.gz` and a `.sha256` file. The version defaults to the git commit (for example `b385ed1`). You can also pass a version yourself, such as `bash deploy/airgap/build-bundle.sh 1.0.0`. Copy both files to the server through your approved transfer process.

## 2. Install on the air-gapped server

```bash
sha256sum -c eft-portal-airgap-<version>.tar.gz.sha256
tar -xzf eft-portal-airgap-<version>.tar.gz
cd eft-portal-airgap-<version>
sh install.sh
```

`install.sh` does three things:
- checks every file against `SHA256SUMS`;
- loads the three images into Docker;
- creates `.env` from `env.example`.

## 3. Configure

Edit `.env`. These two settings are required:

- `POSTGRES_PASSWORD`: a strong database password.
- `PORTAL_BOOTSTRAP_ADMIN_PASSWORD`: the first administrator's password. It must be at least 12 characters and use three of: lower case, upper case, digits, symbols. You must change it at first sign-in. Clear it from `.env` afterwards.

You might also want to change:

- `PORTAL_PORT` and `PORTAL_BIND_ADDRESS`: where users reach the portal.
- `PORTAL_COOKIE_SECURE=true`: set this when a reverse proxy or load balancer serves the portal over HTTPS (recommended).
- Integrations (`JENKINS_*`, `GITHUB_*`, `SONAR_*`, `ARTIFACTORY_*`, Grafana/Prometheus): set `*_MODE=live` and fill in the internal URL and token for each tool the server can reach. Tools left on `mock` show sample data.

**Sign-in.** Local portal accounts work with no identity provider. Microsoft Entra ID (`SPRING_PROFILES_ACTIVE=sso-entra`) works only if both the server and users' browsers can reach `login.microsoftonline.com`.

**AI.** AI is off, and every other feature works without it.

## 4. Start and check

```bash
docker compose --env-file .env up -d
docker compose --env-file .env ps
```

All three containers should show `healthy` within about two minutes. Then open `http://<server>:8081` and sign in as the bootstrap administrator.

## Upgrade

1. Build a new bundle and copy it to the server.
2. Unpack it **in a new folder**, copy your existing `.env` into it, and run `sh install.sh`. The script updates `PORTAL_VERSION` in `.env`.
3. Run `docker compose --env-file .env up -d`.

Database migrations run automatically when the backend starts, and the `pgdata` volume keeps your data. Take a backup before upgrading.

## Backup and restore

```bash
docker compose --env-file .env exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > eftportal-$(date +%F).dump
docker compose --env-file .env exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < eftportal-YYYY-MM-DD.dump
```

## Troubleshooting

- `pull access denied` or `image not found`: the images are not loaded, or `PORTAL_VERSION` in `.env` does not match the bundle. Run `sh install.sh` again.
- The backend never becomes healthy: run `docker compose --env-file .env logs backend`. The usual causes are a wrong database password after the first start, or an invalid bootstrap password.
- Internal tools use HTTPS with a private CA: the CA must be built into the backend image. Put the `.crt` files in `backend/certs/` **before** building the bundle.
