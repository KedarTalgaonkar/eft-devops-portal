# Adding a module or a live client

Every tool follows the same four-part pattern. Copy an existing one (Sonar is the smallest).

## Backend

1. **Models**: `integration/<tool>/<Tool>Models.java` with records for what the portal shows (not the tool's full API).
2. **Port**: `integration/<tool>/<Tool>Client.java`, an interface extending `IntegrationHealth`.
3. **Mock**: `integration/<tool>/Mock<Tool>Client.java`, deterministic data seeded with `new Random(key.hashCode())`,
   annotated
   ```java
   @ConditionalOnProperty(prefix = "portal.integrations.<tool>", name = "mode", havingValue = "mock", matchIfMissing = true)
   ```
4. **Live**: `integration/<tool>/Rest<Tool>Client.java` with `havingValue = "live"`, built on `RestClient`
   (see `RestJenkinsClient`). Throw `IntegrationUnavailableException` on errors so the API answers 502.
   Cache expensive calls with `@Cacheable` on a separate bean (see `JenkinsBuildCache`) and add the cache name to
   `spring.cache.cache-names`.
5. **Controller**: `<feature>/<Feature>Controller.java` under `/api/<feature>` with `@Tag` and `@Operation` for Swagger.
6. **Config**: add `portal.integrations.<tool>.*` to `application.yml`, a field to `IntegrationsProperties`,
   the variables to `.env.example` and the `x-integrations` block in `docker-compose.yml`.
7. **Tests**: unit-test the mapping or aggregation logic; tag anything that needs PostgreSQL with `@Tag("db")`.

The dashboard's integration strip picks up the new client automatically, because it collects every `IntegrationHealth`
bean.

## Frontend

1. `ng g c features/<feature>/<feature>-page` (run it in the container, see README).
2. Add `features/<feature>/<feature>.models.ts` mirroring the backend records and `<feature>.service.ts` using
   `ApiService`.
3. Register the route in `app.routes.ts` and the menu entry in `core/navigation.ts`.
