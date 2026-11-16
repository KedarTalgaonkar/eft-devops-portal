# Security

## Sign-in

Two ways in, both shown on `/login`; either can be turned off.

|                   | Single sign-on (OIDC)                                                                               | Portal (local) accounts                               |
|-------------------|-----------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| For               | Everyone with a corporate account                                                                   | Break-glass admin, service users, sites without SSO   |
| Identity provider | Microsoft Entra ID (`sso-entra` profile); Keycloak in Docker for development (`sso-dev`, default)   | `portal_user` table in PostgreSQL                     |
| Roles             | From the ID token's `roles` (app roles) or `groups` claim, mapped in `portal.security.role-mapping` | Set by an administrator on **Administration → Users** |
| Switch off        | Don't activate an `sso-*` profile                                                                   | `PORTAL_LOCAL_LOGIN=false`                            |

### Design: backend-for-frontend

* The backend runs the OAuth2 authorization-code flow with **PKCE (S256)**, `state` and `nonce`. Tokens stay on the
  server:
  the browser only gets the **`EFTSESSION`** cookie (`HttpOnly`, `SameSite=Lax`, `Secure` when
  `PORTAL_COOKIE_SECURE=true`).
* ID tokens are validated (signature via JWKS, audience, expiry, nonce) and the **issuer is pinned**.
* Sessions live in PostgreSQL (Spring Session JDBC), so they survive restarts and work with several backend replicas.
  Idle timeout `PORTAL_SESSION_IDLE_TIMEOUT` (30 min), absolute limit `PORTAL_SESSION_MAX_AGE` (10 h).
  The session id changes at sign-in (prevents session fixation).
* **CSRF**: double-submit token. The `XSRF-TOKEN` cookie (`SameSite=Strict`, readable by Angular on purpose) must be
  echoed
  in the `X-XSRF-TOKEN` header on every POST/PUT/PATCH/DELETE, including sign-in.
* **Sign-out** ends the portal session and, for SSO users, the identity-provider session too (RP-initiated logout with
  `id_token_hint`).
* `returnTo` after sign-in only accepts same-origin relative paths (no open redirects).

### Roles (deny by default)

| Role     | Can                                                                                   |
|----------|---------------------------------------------------------------------------------------|
| `VIEWER` | Read every dashboard and detail page                                                  |
| `EDITOR` | + import the inventory, edit Jenkins jobs and repositories, run probes and SBOM scans |
| `ADMIN`  | + environments, users, audit log, Swagger UI, actuator                                |

Higher roles include the lower ones. The UI hides what a role can't do; the API enforces it (403 and an audit entry).
SSO users with no mapped role get `PORTAL_DEFAULT_ROLE` (VIEWER); set it empty to refuse them instead.

### Portal accounts

* Passwords: BCrypt (cost 12). Policy: 12–128 characters, 3 of 4 character classes, not containing the username,
  no character 4 times in a row. Shown live on the change-password page.
* **Lockout** after 5 failed attempts for 15 minutes. Errors never say whether the user exists.
* New accounts and password resets get a one-time password; the user must change it before doing anything else.
* Disabling a user, changing roles or resetting a password ends that user's open sessions.
* Admins can't disable or demote themselves.
* **No default password.** The first administrator is created only when `PORTAL_BOOTSTRAP_ADMIN_PASSWORD` is set and no
  administrator exists; it must be changed at first sign-in. Remove the variable afterwards.

### Audit trail

`audit_event` table (and the `AUDIT` log): sign-in success and failure, sign-out, lockouts, denied requests, user
administration, and every change made through the API (who, what, IP, outcome). Viewable at **Administration → Audit
log**.
Kept for 365 days (`portal.security.audit-retention`). Inventory changes also record `changed_by`.

### Rate limiting (nginx)

Sign-in endpoints: 10 requests/minute per IP (burst 10). API: 30 requests/second per IP (burst 60). Excess returns 429.

## Set up Microsoft Entra ID

1. **App registrations → New registration**, single tenant.
   Redirect URIs (Web): `https://<portal-host>/login/oauth2/code/sso` and the sign-out return address
   `https://<portal-host>/login?signedOut` (sent as `post_logout_redirect_uri`).
2. **Certificates & secrets**: new client secret (note its expiry).
3. **App roles**: create `Portal.Viewer`, `Portal.Editor`, `Portal.Admin` (allowed member type: users/groups).
4. **Enterprise applications → the app → Users and groups**: assign groups to the roles.
   Optionally set *Assignment required = Yes* so only assigned people can sign in.
5. In `.env`:
   ```
   SPRING_PROFILES_ACTIVE=prod,sso-entra
   ENTRA_TENANT_ID=<tenant id>
   OIDC_CLIENT_ID=<application (client) id>
   OIDC_CLIENT_SECRET=<secret>
   PORTAL_COOKIE_SECURE=true
   PORTAL_API_DOCS=false
   ```

Role names are matched case-insensitively; change `portal.security.role-mapping` to map group ids instead.

## Local Keycloak (development only)

Started by `docker compose up`, realm `eft` imported from `docker/keycloak/eft-realm.json`, admin console at
http://localhost:8180 (`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`). Test users, each with the matching role:

| User   | Password           | Role   |
|--------|--------------------|--------|
| viewer | `Viewer-Dev-2026!` | VIEWER |
| editor | `Editor-Dev-2026!` | EDITOR |
| admin  | `Admin-Dev-2026!`  | ADMIN  |

These and the client secret `eft-portal-dev-secret` are public test values. Never use this realm outside a laptop.

## Behind HTTPS

Terminate TLS at the ingress and forward `X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-Port`
(nginx in the frontend container passes them on). Set `PORTAL_COOKIE_SECURE=true` and add HSTS at the ingress.
If the identity provider's certificate is issued by the corporate CA, put the CA (`.crt`/`.pem`) into `backend/certs/`
and rebuild; it is added to the JVM trust store (see `backend/certs/README.md`).

## OWASP baseline scan

```bash
docker compose up --build -d
docker compose -f compose.security.yml run --rm zap     # OWASP ZAP baseline, report in security/reports/
docker run --rm -v "$PWD/frontend:/app" -w /app node:24-alpine npm audit
```

ZAP exit codes: 0 = all pass, 1 = failures, 2 = warnings only. Rule decisions are in `security/zap-rules.tsv`.

### Latest result (14 Nov 2026, ZAP stable, passive baseline + AJAX spider, sign-in enabled)

| Result                     | Count |
|----------------------------|-------|
| FAIL                       | **0** |
| PASS                       | 60    |
| WARN (accepted, see below) | 1     |
| IGNORE (informational)     | 1     |

`npm audit`: **0 vulnerabilities**. Backend: 63 tests passing, including the security integration tests (anonymous 401,
role 403s, CSRF required, cookie flags, lockout, forced password change, security headers).

#### Accepted / ignored

| ZAP rule                      | Decision       | Why                                                                                                                                                                  |
|-------------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 10010 Cookie No HttpOnly Flag | WARN, accepted | Raised for `XSRF-TOKEN` only: the CSRF cookie Angular must read. It is `SameSite=Strict` and not a credential. The session cookie is `HttpOnly` (asserted by tests). |
| 2 Private IP Disclosure       | WARN, accepted | The inventory lists internal Tomcat hosts by design; it is now only served to signed-in users. Not raised in the latest scan because the API needs sign-in.          |
| 10109 Modern Web Application  | IGNORE         | Informational: tells you to use the AJAX spider, which the scan already does.                                                                                        |

Re-run after adding the AI features (14 Nov 2026): unchanged, 0 FAIL, 60 PASS, the same accepted warning.

## AI features

Details in [ai.md](ai.md). Security-relevant points:

* Only the backend calls the model; keys stay in environment variables; `AI_MODE=off` by default outside the Docker dev
  stack.
* Secrets are removed and IPs, e-mails and internal hosts replaced by placeholders **before** anything is sent; what was
  masked is shown with each answer and written to the audit log.
* Build logs: only the last 64 KB plus error lines. Digest: only aggregated figures already on the dashboard.
* Per-user daily limit, monthly budget, results reused per build; every request is in `ai_usage` and the audit log.
* Model output is schema-constrained JSON rendered as plain text (no HTML), labelled *AI-generated*; regenerating needs
  Editor, writing a digest on demand needs Admin, usage is Admin-only. All AI POSTs need the CSRF token.

## Other controls

* **Headers (nginx, every response):** `Content-Security-Policy` with no `unsafe-inline`: scripts and styles from self
  plus a
  per-request nonce (nginx writes it into `index.html`, Angular applies it via `ngCspNonce`); `frame-ancestors 'none'`,
  `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`; `X-Content-Type-Options`, `X-Frame-Options: DENY`,
  `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`; `server_tokens off`; `index.html` is
  `no-store`.
* **Subresource Integrity** on all bundles (including lazy chunks via the import map). Fonts and icons are self-hosted:
  no third-party origins.
* **Errors:** no stack traces or exception text to clients; unexpected errors return a reference id that is logged
  server-side. Bad parameters and malformed JSON return 400 without Java type names.
* **API surface:** same origin only (no CORS); actuator exposes only `health` and `info` anonymously; uploads limited to
  10 MB `.xlsx` (Apache POI with its zip-bomb and XXE protections). The backend port is bound to 127.0.0.1.
* **Containers:** backend runs as a non-root user on a JRE-only alpine image; secrets only from environment (`.env`,
  git-ignored).

## Open items

1. **Authenticated ZAP scan**: the baseline only sees public pages now. Run a ZAP scan with a VIEWER session cookie (and
   an active scan in a test environment).
2. **Database password**: change the `eft` default in `.env` before any shared deployment.
3. **Container image CVE scan**: Trivy cannot download its vulnerability DB here because corporate TLS inspection
   re-signs
   HTTPS (`x509: certificate signed by unknown authority`). Fix by adding the corporate root CA to the scanner
   (`-v corp-ca.pem:/etc/ssl/certs/corp-ca.pem`) or pointing `--db-repository` at an internal mirror, then:
   `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image --severity HIGH,CRITICAL eft-devops-portal-backend`
4. **TLS**: the stack serves plain HTTP on localhost; put it behind the corporate TLS ingress (see *Behind HTTPS*).
