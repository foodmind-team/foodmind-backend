# Branch 01 — PostgreSQL, Flyway, and Test Foundation

## Branch metadata

- **Branch:** `chore/backend-test-postgres`
- **Base:** latest protected `master`
- **BE items:** BE-001, BE-002, BE-003, BE-004, BE-009
- **Database deliverable:** immutable, clean-database-validated V1–V11 schema
  foundation
- **Postman folder:** `00 - Platform Health`
- **Blocks:** every later branch

## Scope

Establish a reproducible Spring Boot/PostgreSQL development baseline, enable
Flyway, define safe configuration profiles, prove migrations against real
PostgreSQL with Testcontainers, and add architecture-boundary tests.

### Explicit non-scope

- No user-facing business endpoint.
- No H2 or in-memory substitute for PostgreSQL integration tests.
- No authentication or domain entity implementation.
- No production secret values.

## Target files

```text
pom.xml
.env.example
compose.yaml
src/main/resources/application.properties
src/main/resources/application-local.properties
src/main/resources/application-staging.properties
src/main/resources/application-production-demo.properties
src/main/resources/db/migration/V1__platform_extensions.sql
src/main/resources/db/migration/V2__identity_and_auth.sql
src/main/resources/db/migration/V3__preferences.sql
src/main/resources/db/migration/V4__catalogue.sql
src/main/resources/db/migration/V5__groups_and_records.sql
src/main/resources/db/migration/V6__saved_search_and_explore.sql
src/main/resources/db/migration/V7__recommendations.sql
src/main/resources/db/migration/V8__cooking.sql
src/main/resources/db/migration/V9__chat.sql
src/main/resources/db/migration/V10__cross_cutting_and_analytics.sql
src/main/resources/db/migration/V11__seed_catalogue.sql
src/test/resources/application-test.properties
src/test/java/com/foodmind/foodmindbackend/FoodmindBackendApplicationTests.java
src/test/java/com/foodmind/foodmindbackend/support/PostgreSqlContainerSupport.java
src/test/java/com/foodmind/foodmindbackend/architecture/ModuleBoundaryTest.java
docs/operations/local-development.md
docs/architecture/decisions/ADR-001-modular-monolith.md
docs/architecture/decisions/ADR-002-jwt-refresh-sessions.md
docs/architecture/decisions/ADR-003-permission-query-model.md
```

## Detailed implementation steps

1. **Record the foundation ADRs (BE-001)**
   - ADR-001 freezes the modular-monolith boundary, package dependency rule,
     synchronous integration choice, and conditions that would justify a
     future service split.
   - ADR-002 freezes access-JWT validation, rotating opaque refresh sessions,
     Web/Android transport, reuse detection, account-status checks, and secret
     storage/rotation ownership.
   - ADR-003 freezes owner derivation from JWT, query-level owner/group
     predicates, active-membership reauthorization, non-disclosing `404`
     policy, internal service plus delegation identity, and revocation
     behavior.
   - Each ADR records context, decision, alternatives, consequences, security
     implications, status, owner, and approval date. Close or explicitly defer
     every decision gate referenced by the architecture plan.
   - Obtain review acceptance before security or persistence code begins.

2. **Dependency baseline**
   - Add `spring-boot-starter-validation`,
     `spring-boot-starter-oauth2-resource-server`,
     `spring-boot-starter-actuator`, `flyway-core`,
     `flyway-database-postgresql`, and a Spring Boot 4 compatible
     `springdoc-openapi-starter-webmvc-ui`.
   - Add test-scoped `spring-boot-testcontainers`, Testcontainers JUnit Jupiter,
     PostgreSQL Testcontainers, and ArchUnit.
   - Keep versions under Spring Boot dependency management where supported.
   - Do not add H2, Redis, Kafka, a reactive starter, or a second ORM.

3. **Base configuration**
   - Keep shared non-secret defaults in `application.properties`.
   - Set `spring.jpa.open-in-view=false`,
     `spring.jpa.hibernate.ddl-auto=validate`, UTC JDBC timezone, Flyway
     locations/naming validation, graceful shutdown, safe Jackson unknown-field
     handling, and bounded Hikari settings.
   - Put local datasource defaults only in the `local` profile.
   - Require environment variables in staging/production-demo profiles.
   - Expose only `health`, `info`, and selected metrics; never expose
     environment/config endpoints publicly.

4. **Local PostgreSQL**
   - Add one `postgres` service with a named volume and `pg_isready`
     healthcheck.
   - Use safe local defaults overridable through environment variables.
   - Document startup, reset, migration inspection, and shutdown commands.
   - Never commit populated `.env`.

5. **Commit the coordinated V1–V11 PostgreSQL schema**
   - V1 enables `pg_trgm` and defines only reusable database helpers.
   - V2–V4 establish identity, sessions, normalized preferences, and the
     controlled catalogue.
   - V5–V7 establish groups, media/records, authorised search/Explore,
     recommendation sessions/candidates/reasons/feedback, and group shares.
   - V8–V9 establish Cooking and Chat snapshots/references.
   - V10 establishes central idempotency, append-only audit data, analytics
     views, and the restricted explicit-label ML export source.
   - V11 inserts only deterministic, synthetic, shareable catalogue fixtures.
   - Review every named check, foreign key, partial unique/index, generated
     search vector, state-transition trigger, append-only trigger, and view.
   - Run V1–V11 from an empty PostgreSQL database and verify a previous-version
     upgrade path. The supplied validation baseline is PostgreSQL 18.4 with 48
     base tables, 10 analytics views, and one restricted ML export-source view.
   - Treat every committed V1–V11 file as immutable. Later feature branches use
     these objects and create V12+ only for forward fixes.

6. **PostgreSQL integration support**
   - Define a reusable static `PostgreSQLContainer`.
   - Apply `@ServiceConnection`.
   - Activate the test profile.
   - Run the full Spring context so Flyway migrates and Hibernate validates.
   - Use `@Testcontainers(disabledWithoutDocker = true)` locally only if the
     team explicitly accepts skipping without Docker; CI must run with Docker
     and must not skip.

7. **Migration tests**
   - Assert Flyway reports V1 through V11 successful and in order.
   - Query `pg_extension` and assert `pg_trgm` exists.
   - Assert expected schema objects, deterministic seed keys, and generated
     search columns exist.
   - Exercise representative positive and negative fixtures for state,
     visibility, owner/media consistency, exactly-one-source, rank, feedback,
     and append-only constraints.
   - Explicitly prove that every lower-bound-only `numeric(p,s)` field rejects
     PostgreSQL `NaN`, recommendation/user coordinate pairs are all-or-none, a
     saved distance cannot exist without its coordinate pair, feature
     schema/snapshot pairs are complete, and a `RETURNED` candidate cannot omit
     type or rank.
   - Prove a refresh-session replacement cannot cross user or token-family
     boundaries and that the raw ML export-source view is granted only to the
     dedicated Backend exporter role.
   - Prove refresh rotation/revocation, media finalisation/deletion,
     recommendation session/candidate/reason, and Cooking parent/child
     lifecycles cannot reverse or mutate immutable evidence.
   - Invoke bounded V6 search/Explore keyset functions, share a Chat reference
     into an empty session, and prove the ML decision window/observation cutoff.
   - Fail on out-of-order, checksum mismatch, or invalid migration naming.
   - Add a CI job that runs from an empty PostgreSQL container.

8. **Architecture test**
   - Establish top-level feature packages.
   - Assert controllers do not depend on persistence repositories.
   - Assert domain packages do not depend on Spring MVC, JPA, or integration
     packages.
   - Assert feature modules do not import another feature's infrastructure
     package.

## Commit plan

1. In `docs/architecture/decisions`:

   ```text
   docs(architecture): record backend foundation decisions
   ```

   Commit ADR-001 through ADR-003 only, after their decision gates are
   reviewed.

2. In repository root:

   ```text
   chore(build): add backend runtime and PostgreSQL test dependencies
   ```

   Commit `pom.xml` only.

3. In repository root:

   ```text
   chore(db): configure PostgreSQL Flyway and local runtime
   ```

   Commit profiles, `.env.example`, `compose.yaml`, and runbook updates.

4. In `src/main/resources/db/migration`:

   ```text
   chore(db): add complete FoodMind PostgreSQL schema
   ```

   Commit exactly V1–V11 together. Include no Java feature implementation in
   this commit.

5. In `src/test`:

   ```text
   test(db): validate Flyway with PostgreSQL Testcontainers
   ```

   Commit Testcontainers support and migration/context tests.

6. In
   `src/test/java/com/foodmind/foodmindbackend/architecture`:

   ```text
   test(architecture): enforce backend module boundaries
   ```

## Verification

```powershell
docker compose config
docker compose up -d postgres
docker compose ps
.\mvnw.cmd clean test
```

Start the Backend in terminal A and leave it running:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

After health is ready, run the branch folder in terminal B:

```powershell
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "00 - Platform Health"
```

Expected:

- PostgreSQL becomes healthy.
- V1 through V11 are `Success` in `flyway_schema_history`.
- the expected 48 product base tables (49 when Flyway's own
  `flyway_schema_history` is counted), 10 analytics views, and one restricted
  ML export-source view exist and V11 fixture UUIDs are stable;
- Spring context loads.
- Newman reports zero failed assertions for `00 - Platform Health`.
- no database password or JWT/service token appears in Git diff or logs.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `00 - Platform Health`:

1. `GET {{serviceUrl}}/actuator/health`
   - expect `200`;
   - assert `status === "UP"`;
   - assert response does not contain environment variables or credentials.
2. `GET {{serviceUrl}}/actuator/info`
   - expect `200`;
   - tolerate an empty info object during foundation work.

## Pull Request document

**Title**

```text
chore: establish PostgreSQL, Flyway, and backend test foundation
```

**Body**

```markdown
## Summary

Establishes the PostgreSQL/Flyway development baseline and real-PostgreSQL
integration-test foundation required by all FoodMind backend features.

## Implements

- BE-001, BE-002, BE-003, BE-004, BE-009
- accepted modular-monolith, session, and permission ADRs
- PostgreSQL local runtime and safe configuration contract
- immutable Flyway V1–V11 PostgreSQL schema and deterministic catalogue seed
- Testcontainers migration/context tests
- initial ArchUnit module rules

## Not included

- domain entities or public business endpoints
- authentication
- production secret values

## Database

- Migrations: `V1__platform_extensions.sql` through
  `V11__seed_catalogue.sql`
- Empty-database migration: [PASS/FAIL + evidence]
- Previous-version upgrade migration: [PASS/FAIL + evidence]
- Expected object/constraint/seed fixture checks: [PASS/FAIL + evidence]
- Forward fix required: [No / describe]

## Verification

- `docker compose config`: [PASS]
- `mvnw clean test`: [PASS]
- Postman `00 - Platform Health`: [PASS]

## Configuration

Added variable names only: [list]. No values are committed.

## Risks / follow-up

[List any Docker, CI, version, or environment follow-up.]

## Architecture / data flow

[Describe controller -> application use case -> domain policy -> outbound port /
adapter flow. State where authorization is enforced in SQL and prove every
remote call occurs outside a database transaction. Use `N/A` with a reason only
when a boundary does not exist in this branch.]

## Delivery evidence

- Linked BE/UC issues: [BE-___](https://tracker.example.test/BE-___), [UC-___](https://tracker.example.test/UC-___) — replace both with approved tracker URLs
- Exact commands executed and exit/results: [paste verbatim]
- Postman folder and Newman report: [folder + zero-failure result + report link]
- Redacted response excerpt or screenshot: [link]
- Configuration variable names added/changed (no values): [None / list]
- Explicit non-scope: [list]
- Migration rollback/forward-fix impact: [None / next V12+ migration and impact]
- Risks/follow-ups and owners: [None / linked items]
- Cross-repository actions: [None / linked actions]

## Checklist

- [ ] Clean checkout verified
- [ ] ADR-001 through ADR-003 are accepted
- [ ] Docker-backed tests ran in CI
- [ ] All V1–V11 checksums are fixed after merge
- [ ] 48 product base tables, Flyway history, 10 analytics views, one
      restricted ML export-source view, and deterministic seed identifiers are
      verified
- [ ] No secrets or personal data committed
- [ ] Runbook matches actual commands
- [ ] Review conversations resolved
```
