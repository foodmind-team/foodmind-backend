# Branch 17 - Cloud, Security, UAT, and Release Freeze

## Branch metadata

- **Branch:** `chore/cloud-security-uat`
- **Base dependency:** Branch 16 merged, or Branch 15 when optional media is
  explicitly deferred
- **BE items:** BE-031 through BE-040
- **Database objects:** `idempotency_record`, `audit_event`, analytics views,
  and operational indexes from the schema foundation
- **Postman folder:** `16 - Security and UAT`
- **Exit:** production-demo release candidate with retained evidence

## Scope

Harden the complete backend, prove recovery and authorization behaviour, verify
database plans at representative volume, package and deploy an immutable
container, establish required CI/security gates, execute shared UC-01 through
UC-09 UAT, freeze contracts, and finish clean-room operating documentation.

This branch is evidence-heavy but may contain narrowly scoped implementation
fixes found by the hardening work. A material feature or contract redesign must
return to its owning feature branch/issue instead of being hidden here.

### Explicit non-scope

- No new product feature, Admin API, pantry automation, poll, or public feed.
- No unreviewed cloud resource or provider expansion.
- No production secret, real access token, personal data, or raw conversation
  content in Git or retained evidence.
- No destructive database rollback of a shared Flyway migration.

## Target files

```text
Dockerfile
.dockerignore
.github/workflows/backend-ci.yml
.github/dependabot.yml
compose.yaml
pom.xml
src/main/java/com/foodmind/foodmindbackend/common/config/
src/main/java/com/foodmind/foodmindbackend/common/observability/
src/main/java/com/foodmind/foodmindbackend/common/security/
src/main/java/com/foodmind/foodmindbackend/common/idempotency/
src/main/java/com/foodmind/foodmindbackend/common/audit/
src/main/java/com/foodmind/foodmindbackend/recommendation/application/recovery/
src/main/java/com/foodmind/foodmindbackend/cooking/application/recovery/
src/main/resources/application-staging.properties
src/main/resources/application-production-demo.properties
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/security/
src/test/java/com/foodmind/foodmindbackend/migration/
src/test/java/com/foodmind/foodmindbackend/performance/
src/test/resources/fixtures/uat/
docs/operations/deployment.md
docs/operations/incident-and-recovery.md
docs/operations/demo-reset.md
docs/security/permission-review.md
docs/security/zap-disposition.md
docs/testing/uat-matrix.md
docs/testing/uat-evidence/
docs/contracts/freeze-manifest.md
README.md
```

Adapt CI/IaC paths to the repository's selected provider. Do not add placeholder
cloud resources that cannot be reviewed or reproduced.

## Detailed implementation steps

### 1. Stale workflow recovery and dependency health (BE-031)

1. Enumerate persisted workflow states for recommendation, cooking, media, and
   idempotency records.
2. Define an explicit stale threshold per state and which transitions a recovery
   worker may perform. Use database locking or compare-and-set version checks so
   two workers cannot complete the same recovery.
3. Mark irrecoverable work with a safe `failure_code`; never fabricate a
   successful model/cooking result.
4. Expire safe cached idempotency responses according to documented retention.
5. Add time-bounded scheduled recovery with batch/page limits.
6. Expose separate dependency indicators for database, Agent, object storage,
   and schema compatibility. CRUD readiness must not fail only because Agent is
   unavailable and deterministic recommendation fallback still works.
7. Add metrics for stale transitions, recovery outcomes, dependency latency,
   Agent failure/version mismatch, fallback reason, and candidate count.
8. Demonstrate injected timeout, invalid schema, unavailable Agent, interrupted
   processing, and recovery after restart.

### 2. Permission-query review and negative matrix (BE-032)

1. Inventory every protected repository query and state transition by endpoint.
2. Verify identity comes only from the validated principal; request owner IDs
   must never widen access.
3. Put owner/group/current-membership/deleted/status predicates in the query or
   locked transition, not only in a controller pre-check.
4. Test owner, unrelated user, active member, invited member, left member,
   removed member, group owner, and service/delegated identity as applicable.
5. Use `404` where ID existence must not be disclosed; use `403` only for
   authenticated actions whose target may safely be acknowledged.
6. Re-authorise saved and chat references at resolution time.
7. Review mass assignment, pageable bounds, sort-field allow-listing, UUID
   parsing, optimistic locking, and soft-deleted rows.
8. Record reviewer, commit SHA, query/test link, decision, and follow-up in
   `docs/security/permission-review.md`.

### 3. PostgreSQL index and plan verification (BE-033)

1. Generate deterministic, non-personal representative volume for each large
   table without adding production-like personal data to V11.
2. Execute `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` for:
   - owner food/drink history;
   - active group feed with membership authorization;
   - authorised full-text/trigram search;
   - Want to Try resolution;
   - recommendation history/candidate load;
   - weekly dashboard/recap;
   - stale workflow batches.
3. Set measured acceptance budgets and record row counts, PostgreSQL version,
   hardware/environment, planning/execution time, scans, and buffers.
4. Add only indexes justified by the plans through a new forward migration.
   Never edit a migration already shared.
5. Re-run empty-database migration, upgrade migration, and representative plans.
6. Store concise plan summaries rather than machine-specific megabytes in the
   PR; retain full sanitized artifacts in the agreed evidence location.

### 4. Immutable container and configuration contract (BE-034)

1. Use a multi-stage build or reproducible prebuilt-JAR image.
2. Run as a numeric non-root user on a minimal supported JRE image.
3. Copy only the built artifact and required runtime files.
4. Pin the base image by reviewed version/digest according to the team's update
   policy; scan both dependencies and image.
5. Set a read-only root filesystem where hosting permits, a writable temporary
   directory, graceful shutdown, memory-aware JVM settings, and a health check.
6. Define all non-secret environment variable names, required/optional status,
   formats, defaults, and owning profile. Never bake secrets into image layers.
7. Confirm logs go to stdout/stderr as structured events without tokens,
   dietary details, comments, prompts, chat content, or signed URLs.
8. Prove the same image digest runs in staging and production-demo.

### 5. Staging/production-demo deployment (BE-035)

1. Deploy the Backend only behind HTTPS with a narrowly configured ingress/load
   balancer and explicit trusted proxy handling.
2. Use private PostgreSQL/RDS connectivity, TLS, encryption at rest, backups,
   bounded connection pool, and a least-privilege application role.
3. Run Flyway once per deployment strategy and retain the schema-history result.
   Prevent concurrent migration races.
4. Keep Agent/Intelligence on a private route with service authentication,
   delegation-token validation, outbound allow-listing, timeout, and correlation
   propagation.
5. Store secrets in the cloud secret manager and document rotation. Do not place
   values in Git, CI logs, task definitions, or Postman exports.
6. Restrict CORS to reviewed Web origins; Android/native calls do not justify
   wildcard browser origins.
7. Configure backups, retention, deletion/anonymisation procedure, alarms, log
   retention, and cost/resource limits.
8. Retain HTTPS URL, image digest, Git SHA, Flyway version, health evidence, and
   private-route proof with secrets redacted.

### 6. Required CI checks (BE-036)

Create independent required jobs for:

- compile and unit tests;
- PostgreSQL Testcontainers integration and migration-from-empty;
- migration upgrade/checksum/naming validation;
- architecture rules;
- OpenAPI lint and breaking-change detection against the frozen baseline;
- Backend-Agent/ML fixture schema checks;
- dependency and license review;
- secret scan;
- static analysis;
- container build, SBOM, and vulnerability scan;
- Postman/Newman smoke run against an ephemeral environment where feasible; and
- documentation link/JSON/SQL artifact validation.

Pin third-party CI actions to reviewed immutable versions according to policy.
Use least-privilege workflow permissions and never run untrusted pull-request
code with deployment credentials. Only the protected release workflow may
deploy an already-tested digest.

### 7. OWASP ZAP and application security baseline (BE-037)

1. Run the ZAP baseline against the deployed staging API through its intended
   public entry point.
2. Authenticate only through a dedicated, least-privilege test account and
   redact session material from reports.
3. Cover security headers, CORS, TLS redirect, content types, error leakage,
   method handling, authentication rate limits, and representative protected
   paths.
4. Triage every finding with severity, exploitability, owner, remediation or
   time-bounded documented acceptance.
5. No Critical or High finding and no P0/P1 product-security issue may remain
   open for the demo release.
6. Re-run after fixes and retain sanitized report hash, tool version, target
   commit/environment, date, and disposition.

### 8. Shared UAT and client parity (BE-038)

1. Materialize UC-01 through UC-09 as independent rows in
   `docs/testing/uat-matrix.md`.
2. For each case record: fixture/seed version, preconditions, exact API flow,
   Android result, Web result, expected/actual outcome, tester, UTC date,
   environment, Backend/Web/Android/Agent/ML commit or artifact versions, and
   evidence link.
3. Include normal, empty, invalid, unauthorized, forbidden/non-disclosing,
   concurrency, timeout, fallback, revoked membership/reference, timezone, and
   multi-currency cases.
4. Reset data through the documented deterministic reset process. Never make
   test order depend on unexplained shared state.
5. Run the full Postman collection first as an API contract smoke test, then
   Android/Web parity against the same fixture IDs.
6. Create an owning issue for every deviation; rerun affected cases after the
   exact fix is deployed.

### 9. Contract and fixture freeze (BE-039)

1. Generate and validate the OpenAPI artifact from the committed source of
   truth. Confirm all clients consume the same version.
2. Freeze public examples, stable error codes, pagination semantics, enums,
   and idempotency behaviour.
3. Freeze Backend-Agent, delegated-tool, and ML snapshot JSON schemas plus
   positive/negative fixtures and supported contract versions.
4. Produce a manifest containing path, semantic version, SHA-256 checksum,
   owner, consumers, and compatibility policy for every frozen artifact.
5. Run breaking-change detection and consumer tests before tagging.
6. Write migration/release notes for renamed/added/removed fields and any
   temporary compatibility behaviour.
7. Tag only the reviewed commit and reference the immutable image digest.

### 10. Runbooks, reset, and clean-room rehearsal (BE-040)

1. Update README quick start, architecture links, profile/configuration
   contract, test commands, and documentation index.
2. Document deployment, migration/forward-fix, rollback of application image,
   backup/restore, secret rotation, Agent outage/fallback, stale recovery,
   media cleanup, and incident escalation.
3. Provide a safe demo reset that targets only the named demo environment,
   verifies the target before mutation, migrates from empty, loads V11
   deterministic seed data, and prints the resulting contract/seed version.
4. Have a person or clean environment with no unrecorded local state follow the
   runbook from checkout to UAT smoke.
5. Record duration, commands, deviations, fixes, final commit/image/schema
   versions, and approver. Repeat until it succeeds without tribal knowledge.

## Commit plan

Keep every commit independently green. If hardening finds a feature defect,
prefer a small commit using that feature's scope and call it out in the PR.

1. `feat(operations): recover stale workflows and expose safe dependency health`
2. `test(security): complete permission and abuse-case matrix`
3. `perf(db): verify query plans and add measured indexes`
4. `chore(container): package immutable non-root backend image`
5. `chore(ci): require backend contract security and container checks`
6. `chore(deploy): define staging and production-demo release contract`
7. `test(security): retain ZAP baseline and remediation evidence`
8. `test(uat): record shared use-case and client parity evidence`
9. `docs(api): freeze public and internal contract manifests`
10. `docs(operations): complete release runbooks and demo rehearsal`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | recovery workers, safe health/metrics configuration, focused recovery tests, and `docs/operations/incident-and-recovery.md` |
| 2 | permission/abuse tests plus `docs/security/permission-review.md`; feature fixes use their owning feature paths |
| 3 | the next V12+ migration only when justified, performance fixtures/tests, and sanitized query-plan notes |
| 4 | `Dockerfile`, `.dockerignore`, container-specific configuration/tests |
| 5 | `.github/workflows/backend-ci.yml`, dependency automation, and CI validation scripts/config only |
| 6 | reviewed deployment/IaC files and `docs/operations/deployment.md`; never secret values |
| 7 | security fixes plus sanitized `docs/security/zap-disposition.md` |
| 8 | `src/test/resources/fixtures/uat/`, `docs/testing/uat-matrix.md`, and sanitized evidence index |
| 9 | OpenAPI/internal fixtures, `docs/contracts/freeze-manifest.md`, and migration/release notes |
| 10 | README plus operations/demo-reset/rehearsal documentation |

If a new migration is required by measured plan work, commit it with the
specific query change in item 3 using:

```text
perf(db): add measured indexes for authorised read paths
```

## Verification commands

Adapt provider commands to the chosen platform and record exact versions.

```powershell
.\mvnw.cmd clean verify
docker build --pull --tag foodmind-backend:release-candidate .
docker inspect foodmind-backend:release-candidate
docker run --rm --read-only --user 10001:10001 foodmind-backend:release-candidate
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Staging.postman_environment.json `
  --reporters cli,junit
```

The Newman command above is the full-release command when Branch 16 is
included. When media is formally deferred, execute every applicable folder
explicitly and omit only `15 - Media Upload`:

```powershell
$releaseFolders = @(
  "00 - Platform Health",
  "01 - API Conventions",
  "02 - Authentication",
  "03 - User and Preferences",
  "04 - Catalogue",
  "05 - Food Records",
  "06 - Groups and Visibility",
  "07 - Drink Records and History",
  "08 - Saved, Search and Explore",
  "09 - Recommendation Fallback",
  "10 - Recommendation Agent",
  "11 - Recommendation Feedback",
  "12 - Cooking Plans",
  "13 - Chat and Grounding",
  "14 - Dashboard and Recap",
  "16 - Security and UAT"
)

foreach ($folder in $releaseFolders) {
  newman run postman/FoodMind-Backend.postman_collection.json `
    -e postman/FoodMind-Staging.postman_environment.json `
    --folder $folder `
    --reporters cli
  if ($LASTEXITCODE -ne 0) {
    throw "Newman failed for $folder"
  }
}
```

Also retain:

- empty and previous-version Flyway results;
- OpenAPI lint/breaking report;
- contract-fixture checksums;
- SBOM, dependency/secret/container scan summaries;
- representative PostgreSQL plan summaries;
- ZAP report and disposition;
- full Postman run report;
- UC-01 through UC-09 parity matrix; and
- clean-room rehearsal record.

No secret, token, personal/dietary content, signed upload URL, raw prompt, or
chat message may appear in retained evidence.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `16 - Security and UAT` contains negative/security assertions that reuse
IDs and sessions established by earlier folders:

1. No token on a protected route -> `401` safe envelope.
2. Malformed/expired/revoked token -> `401`, no parsing details.
3. Valid secondary token against primary private record -> non-disclosing `404`.
4. Removed group member against feed/reference -> denied immediately.
5. Reuse an idempotency key with the same payload -> same safe outcome/resource.
6. Reuse the key with a different payload -> `409` stable error.
7. Unsupported sort/filter, excessive page size, malformed UUID, and unknown
   JSON field -> bounded `400` errors.
8. Correlation ID is returned; stack trace, SQL, token, prompt, model body, and
   private metadata are absent.
9. Agent-unavailable fixture -> API remains available and recommendation uses
   deterministic fallback.
10. Health/readiness response exposes no configuration or dependency secrets.
11. Archived/deleted/revoked resources stay inaccessible.
12. Final smoke requests cover UC-01 through UC-09 and assert every captured ID
    belongs to the active environment.

Run folders `00` through `14`, optional media folder `15` when Branch 16 is in
the release, and then folder `16`. If media was explicitly deferred, retain the
approval and mark folder `15` `N/A — media deferred`; do not report an unrun
folder as passed. Security scanner execution itself is not embedded in Postman;
link its separate report in the PR.

## Pull Request document

**Title**

```text
chore(release): harden and freeze the FoodMind backend
```

**Body**

```markdown
## Summary

Hardens, deploys, verifies, and freezes the complete FoodMind production-demo
Backend. This PR carries release evidence and only narrowly scoped fixes found
during hardening.

## Implements

- BE-031 stale recovery, dependency health, and fallback metrics
- BE-032 complete permission-query review
- BE-033 representative PostgreSQL plan verification
- BE-034 immutable non-root container
- BE-035 staging/production-demo deployment contract
- BE-036 required CI checks
- BE-037 ZAP baseline and disposition
- BE-038 UC-01 through UC-09 shared UAT/client parity
- BE-039 public/internal contract freeze
- BE-040 runbooks, demo reset, and clean-room rehearsal

## Release identity

- Git commit/tag: [value]
- image digest: [value]
- Flyway schema version: [value]
- OpenAPI version/checksum: [value]
- Agent/ML contract versions: [values]
- environment and deployment UTC time: [values]

## Database

- empty migration: [PASS + link]
- upgrade migration: [PASS + link]
- representative query plans: [PASS + link]
- new forward migration, if any: [name/reason]
- backup/restore rehearsal: [PASS + link]

## Security

- permission review: [PASS + link]
- dependency/secret/container scans: [PASS + links]
- SBOM: [link]
- ZAP baseline/disposition: [PASS + link]
- open Critical/High or P0/P1 findings: 0

## Contracts and CI

- required checks: [PASS + run link]
- OpenAPI breaking check: [PASS]
- internal fixture consumer tests: [PASS]
- freeze manifest: [link]

## Runtime evidence

- HTTPS/health/readiness: [PASS]
- private database and Intelligence paths: [PASS]
- injected failure/recovery demonstrations: [PASS]
- fallback under Agent outage: [PASS]
- logs/evidence redaction review: [PASS]

## UAT

- Postman folders `00`-`16`: [PASS + report]
- UC-01-UC-09 matrix: [PASS + link]
- Android/Web parity: [PASS + link]
- clean-room rehearsal: [PASS + link]

## Configuration

New/changed variable names only: [list]. Values remain in the approved secret
manager.

## Risks / follow-up

[Accepted low-risk findings, owners, expiry dates, or explicitly deferred scope.]

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

## Release checklist

- [ ] All required CI checks are green
- [ ] No unresolved Critical/High or P0/P1 finding
- [ ] Empty and upgrade migrations pass
- [ ] Permission matrix and query plans are reviewed
- [ ] Postman and UC-01-UC-09 UAT pass on the release digest
- [ ] Contracts and fixture checksums are frozen
- [ ] Backup/restore and demo reset are rehearsed
- [ ] Logs and evidence contain no prohibited data
- [ ] Rollback/incident runbooks name an owner
- [ ] Release tag points to this reviewed commit
```
