# Security baseline (OWASP)

## How to scan
```bash
docker compose up --build -d
docker compose -f compose.security.yml run --rm zap     # OWASP ZAP baseline, report in security/reports/
docker run --rm -v "$PWD/frontend:/app" -w /app node:24-alpine npm audit
```
ZAP exit codes: 0 = all pass, 1 = failures, 2 = warnings only. Rule decisions are in `security/zap-rules.tsv`.

## Latest result (13 Nov 2026, ZAP stable, passive baseline + AJAX spider, 104 URLs)
| Result | Count |
|---|---|
| FAIL | **0** |
| PASS | 60 |
| WARN (accepted, see below) | 1 |
| IGNORE (informational) | 1 |

`npm audit` (production and dev dependencies): **0 vulnerabilities**.

### Accepted / ignored
| ZAP rule | Decision | Why |
|---|---|---|
| 2 Private IP Disclosure | WARN, accepted until phase 4 | The inventory exists to list internal Tomcat hosts. Acceptable only behind sign-in (Entra ID, phase 4). |
| 10109 Modern Web Application | IGNORE | Informational: tells you to use the AJAX spider, which the scan already does. |

## Controls in place
* **Headers (nginx, every response):** `Content-Security-Policy` with no `unsafe-inline`: scripts and styles from self plus a
  per-request nonce (nginx writes it into `index.html`, Angular applies it via `ngCspNonce`); `frame-ancestors 'none'`,
  `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`; `X-Content-Type-Options`, `X-Frame-Options: DENY`,
  `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`; `server_tokens off`; `index.html` is `no-store`.
* **Subresource Integrity** on all bundles (including lazy chunks via the import map). Fonts and icons are self-hosted:
  no third-party origins.
* **Errors:** no stack traces or exception text to clients; unexpected errors return a reference id that is logged
  server-side. Bad parameters and malformed JSON return 400 without Java type names.
* **API surface:** actuator exposes only `health` and `info`; CORS limited to the portal origins; uploads limited to
  10 MB `.xlsx` (Apache POI with its zip-bomb and XXE protections).
* **Containers:** backend runs as a non-root user on a JRE-only alpine image; secrets only from environment (`.env`, git-ignored).

## Open items
1. **Authentication**: no sign-in yet (phase 4: Entra ID, OAuth2 resource server + roles). Until then run the portal only on a
   trusted network. A ZAP *authenticated* or *full* scan should follow once sign-in exists.
2. **Database password**: change the `eft` default in `.env` before any shared deployment.
3. **Container image CVE scan**: Trivy cannot download its vulnerability DB here because corporate TLS inspection re-signs
   HTTPS (`x509: certificate signed by unknown authority`). Fix by adding the corporate root CA to the scanner
   (`-v corp-ca.pem:/etc/ssl/certs/corp-ca.pem`) or pointing `--db-repository` at an internal mirror, then:
   `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image --severity HIGH,CRITICAL eft-devops-portal-backend`
4. **TLS**: the stack serves plain HTTP on localhost; put it behind the corporate TLS ingress and add `Strict-Transport-Security` there.
