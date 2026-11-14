# AI features

Phase 1: **build failure explainer** and **executive digest**. Both use Claude through one backend module
(`com.euronet.eft.portal.ai`); the browser never talks to the model and never sees an API key.

## Modes

| `AI_MODE` | What happens | When |
|---|---|---|
| `off` (application default) | No AI anywhere; buttons and the digest card are hidden | Production until the provider is approved |
| `mock` (default in `docker-compose.yml`) | Built-in sample answers from keyword rules and templates; **nothing leaves the portal** | Demos, development, tests |
| `live` | Claude via the provider in `AI_PROVIDER` | After approval |

Switching to live needs only environment variables; then `docker compose up -d backend`:

| `AI_PROVIDER` | Also set |
|---|---|
| `anthropic` | `ANTHROPIC_API_KEY` |
| `bedrock` | `AWS_REGION` and `AWS_BEARER_TOKEN_BEDROCK` (or AWS credentials / an IAM role). The model id gets the `anthropic.` prefix automatically |
| `foundry` | `ANTHROPIC_FOUNDRY_API_KEY` and `ANTHROPIC_FOUNDRY_RESOURCE` |

`AI_MODEL` defaults to `claude-opus-5-5`. Behind the corporate TLS proxy, put the corporate CA into `backend/certs/`
(see `backend/certs/README.md`) so the backend trusts the provider's certificate.

## What is sent, and what is not

| Feature | Sent to the model | Never sent |
|---|---|---|
| Build failure explainer | Project, client, environment, Jenkins job, build number/result/duration; the **last 64 KB** of the console log (`AI_MAX_LOG_KB`) and up to 80 error lines from earlier in the log | Anything else from Jenkins; the full log |
| Executive digest | The aggregated figures the Command centre already shows: RAG per area, delivery KPIs (current vs previous 30 days), estate counts, the top 12 issues with owners | Logs, source code, credentials |

**Masking before every call** (`Redactor`):
* Secrets become `[SECRET]` and are never restored: `password=`/`token:`/`apiKey=`/`client_secret` style values,
  `--password x` flags, `Authorization:` headers, credentials in URLs, JWTs, GitHub/AWS/Artifactory/Slack/Anthropic
  token formats, private-key blocks.
* IP addresses, e-mail addresses, every host from the inventory and host names ending in `AI_MASK_DOMAINS`
  (default `.internal,.local,.corp,.lan`) become numbered placeholders (`[IP_1]`, `[HOST_2]`, `[EMAIL_1]`). The
  same value always gets the same placeholder, so the model can still reason about "the same host". The portal puts
  the real values back into the answer for display (to signed-in users, who can see them elsewhere anyway).
* The UI shows what was masked under every answer ("2 IP addresses, 1 secret masked"); the audit log records it too.

Add your company domains to `AI_MASK_DOMAINS` (comma-separated) before going live.

## Controls

* **Who:** every signed-in user can ask for a build explanation and rate answers; *Analyse again* (a new paid call)
  needs Editor; *Write a new digest* needs Admin; usage is visible to Admins (**Administration → AI usage**).
* **Limits:** `AI_USER_DAILY_LIMIT` (50) requests per user per day; `AI_MONTHLY_BUDGET_USD` (100) estimated spend per
  calendar month, after which live calls are refused. Estimates use `AI_PRICE_INPUT/OUTPUT/CACHE_READ` (USD per
  million tokens, Claude Opus 5.5 list prices by default); check them against your provider's price list.
* **Reuse:** an explanation is stored per build and reused for everyone, so a build is only paid for once. Digests are
  kept as history.
* **Audit:** every request (user, feature, subject, tokens, estimated cost, outcome, masking) is in `ai_usage` and in
  the audit log (`AI_REQUEST`).
* **Output:** answers are constrained to a JSON schema (structured outputs), shown as text (never HTML), labelled
  *AI-generated* with model and time, and the model is told to say "unclear" rather than guess.
* **Errors:** provider outages, rate limits, refusals and bad credentials become a short message for the user; details
  only go to the server log.

## Executive digest schedule

`AI_DIGEST_CRON` (default `0 0 7 * * MON-FRI`) in `AI_DIGEST_ZONE` (`Asia/Kolkata`); `AI_DIGEST_ENABLED=false` stops
it. In mock mode a sample digest is written at start-up when none exists.

## Not yet enabled

* **Refusal fallback:** Claude can decline content it considers unsafe (rare for build logs). The portal shows a
  message; automatic retry on a fallback model will be added once the provider is chosen (the mechanism differs per
  provider).
* **Quality measurement against real failures:** collect ~30 real failed builds with their known causes and compare the
  explainer's category and fix with them before switching production to live (the mock rules are covered by
  `MockBuildDiagnoserTest`; the thumbs-up/down ratings on the admin page show how it does in practice).

## Code map

| Piece | Where |
|---|---|
| Gateway (limits, masking, call, restore, ledger, audit) | `ai/AiGateway.java` |
| Claude client (Anthropic / Bedrock / Foundry) | `ai/ClaudeModel.java` |
| Masking | `ai/Redactor.java` |
| Build explainer, prompt, mock rules | `ai/builds/` and `integration/jenkins/BuildLog.java` |
| Digest, prompt, facts, mock writer, schedule | `ai/digest/` |
| Stored answers and feedback | `ai/insight/`, tables `ai_insight`, `ai_feedback` (Flyway V5) |
| Usage ledger and admin view | `ai/usage/`, `ai/AiAdminController.java`, table `ai_usage` |
| UI | `core/ai.ts`, `shared/ai-meta.ts`, `features/pipelines/build-explanation.ts`, `features/dashboard/digest-card.ts`, `features/admin/admin-ai.ts` |

Adding a feature: write a result record (its JSON schema is the contract), a system prompt, a mock function, and call
`AiGateway.run(new AiTask<>(...))`; store with `AiInsightService.save` if the answer should be reused.
