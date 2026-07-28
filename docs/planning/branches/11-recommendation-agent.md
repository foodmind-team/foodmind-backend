# Branch 11 — Recommendation Agent Integration

## Branch metadata

- **Branch:** `feat/recommendation-agent`
- **Base dependency:** Branch 10 merged
- **Use case:** UC-04
- **BE items:** BE-022, BE-023, BE-024
- **Private contract:** Backend → Recommendation Agent
- **Schema foundation:** use V7 model/evidence fields committed in Branch 01
- **Postman folder:** `10 - Recommendation Agent`

## Scope

Integrate the private Recommendation Agent behind the existing public
recommendation contract, enforce service authentication/timeouts/versioning,
validate all returned IDs/reasons/evidence/model metadata, and preserve the
deterministic fallback for every upstream failure.

### Explicit non-scope

- No public Agent endpoint or client-to-Agent credential.
- No model training/inference implementation in Backend.
- No unsafe automatic generation retry.
- No change to the frozen public recommendation response solely for Agent data.

## Detailed implementation steps

1. Freeze versioned internal fixtures with `foodmind-intelligence`:
   - request/session/correlation/trace IDs;
   - contract version and absolute deadline;
   - eligible candidate IDs and bounded feature/evidence values;
   - user context without credentials/raw unrelated records;
   - returned ranks/types/reason codes/explanations;
   - model and feature-schema metadata.
2. Define an application port owned by `recommendation`; keep HTTP DTOs inside
   `integration.agent`.
3. Configure one synchronous HTTP client with private base URL, service token,
   JSON limits, connection timeout, read timeout, and correlation propagation.
4. Do not automatically retry generation. A timeout or ambiguous transport
   result falls back; idempotency prevents duplicate public sessions.
5. TX1 inserts the session as `CREATED`, inserts candidates, advances it to
   `PROCESSING`, and commits. Call Agent only afterward; TX2 inserts reasons
   before advancing candidates and makes the terminal session update last.
6. Validate response:
   - matching session/request/trace;
   - supported contract version;
   - status/model metadata;
   - candidate IDs are unique and belong to eligible input;
   - maximum three, contiguous ranks;
   - valid candidate types, diversity when possible;
   - reason codes allow-listed and supported by stored evidence;
   - explanation text bounded and contains no unsupported claims.
7. Persist model version/status, feature schema, candidate scores/feature
   snapshot where allowed, Agent trace, and validated reasons.
8. Failure mapping:
   timeout, connection, non-2xx, malformed JSON, schema mismatch, unknown ID,
   invalid reason, unsupported version, and inference-unavailable all produce a
   recorded fallback reason and execute fallback v1.
   Persist only a V7-allowed tuple: no terminal `PENDING` model state, no
   successful model state paired with fallback success, and a safe
   `failure_code` on overall `FAILED`.
9. Never return raw upstream payload/error. Log only IDs, status, duration,
   version, and safe failure code.
10. Add metrics for Agent latency, failure type, schema rejection, model
    version, and fallback rate.

## Implementation symbols and query contracts

| Layer | Exact symbol | Required contract |
| --- | --- | --- |
| Port | `RecommendationAgentPort#generate` | Accepts only a bounded, versioned command containing eligible candidates and an absolute deadline; returns a transport-neutral result. |
| Adapter | `RecommendationAgentHttpAdapter#generate` | Adds private service authentication/correlation, enforces connection/read/payload limits, performs no automatic generation retry, and maps transport failures to safe codes. |
| Transaction | `RecommendationTransactionService#createProcessingSession` | TX1 persists the session, request/evidence snapshot, and eligible candidates, commits, and returns the immutable Agent command. |
| Orchestration | `GenerateRecommendation#invokeAgentOutsideTransaction` | Calls the port only after TX1 commits; a delayed stub test must observe no active transaction. |
| Validation | `AgentResultValidator#validate` | Checks request/session/trace/version, eligible unique IDs, ranks, types, reasons, evidence, text bounds, and model metadata before any result is trusted. |
| Transaction | `RecommendationTransactionService#completeFromAgent`, `completeWithFallback` | TX2 re-loads/locks the owner session, verifies it is still completable, then atomically persists either validated Agent output or fallback status/reason. |
| Read query | `RecommendationSessionQueryAdapter#findOwnedResult` | Includes `user_id = :actorId` in SQL and returns the same ordered candidate projection used by Branch 10. |

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/recommendation/application/port/RecommendationAgentPort.java
src/main/java/com/foodmind/foodmindbackend/integration/agent/AgentClientConfiguration.java
src/main/java/com/foodmind/foodmindbackend/integration/agent/RecommendationAgentHttpAdapter.java
src/main/java/com/foodmind/foodmindbackend/integration/agent/dto/
src/main/java/com/foodmind/foodmindbackend/recommendation/application/GenerateRecommendation.java
src/main/java/com/foodmind/foodmindbackend/recommendation/domain/AgentResultValidator.java
src/test/resources/contracts/agent/recommendation/
src/test/java/com/foodmind/foodmindbackend/integration/agent/
src/test/java/com/foodmind/foodmindbackend/recommendation/AgentResultValidatorTest.java
```

## Commit plan

1. `docs(api): freeze recommendation Agent contract fixtures`
2. `feat(recommendation): call private recommendation Agent`
3. `feat(recommendation): validate and persist Agent evidence`
4. `feat(recommendation): fall back on all upstream failures`
5. `test(recommendation): cover Agent contract and invalid outputs`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | versioned positive/negative fixtures under `src/test/resources/contracts/agent/recommendation/` and their schema/contract notes |
| 2 | `RecommendationAgentPort`, HTTP configuration/adapter/DTOs, and transport tests |
| 3 | result validator, TX2 persistence integration, model/evidence mapping, and focused tests |
| 4 | failure classification/fallback wiring, metrics, safe logging, and focused timeout/malformed-output tests |
| 5 | remaining producer/consumer, malicious-output, payload-limit, and transaction-boundary tests |

Do not edit V7. Use a V12+ forward migration only if the frozen Agent contract
proves that a persisted field or constraint must change.

## Tests

- valid normal/cold-start responses;
- model available/unavailable;
- connection refusal and timeout;
- unsupported contract/feature schema;
- unknown, duplicated, filtered, or over-limit candidate;
- bad rank/type/reason/evidence;
- oversized/malformed response;
- safe public response and fallback persistence;
- no open DB transaction during stubbed delayed call.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*RecommendationAgent*,*AgentResultValidator*,*RecommendationTransaction*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "10 - Recommendation Agent"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
timeout, unavailable, malformed, and malicious Agent fixtures all produce a
persisted deterministic fallback without leaking upstream data, and the delayed
stub proves the remote call occurs outside a database transaction.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `10 - Recommendation Agent`:

1. Generate with Agent fixture enabled; assert `modelStatus`/version and Agent
   trace.
2. Agent cold-start response; assert availability metadata and valid result.
3. Configure Agent timeout fixture; assert `FALLBACK_SUCCEEDED`.
4. Configure invalid-output fixture; assert fallback and no raw body.
5. Fetch session and verify persisted result/status is stable.

The private Agent endpoint itself is not included in the public collection.
Fixture mode is selected through test/staging configuration, never by a public
request field.

## Pull Request document

**Title**

```text
feat(recommendation): integrate and validate private Agent results
```

**Body**

```markdown
## Summary

Adds the private Recommendation Agent behind the unchanged public UC-04
contract, with strict validation and deterministic fallback.

## Implements

- BE-022, BE-023, BE-024

## Internal contract

- Agent contract version: [value]
- fixture commit/version: [value]
- timeout budget: [value]

## Validation/fallback

[List ID/rank/type/reason/model checks and safe failure codes.]

## Database / forward fix

- Uses immutable V7 session/candidate/model/evidence columns.
- New migration: [None / V12+ filename and reason]
- Forward-fix and application rollback impact: [describe]

## Verification

- contract/invalid-output tests: [PASS]
- full Maven tests: [PASS]
- Postman `10 - Recommendation Agent`: [PASS]
- DB-transaction boundary check: [PASS]

## Cross-repository action

- Intelligence fixture/version: [link]
- ML model-package compatibility: [version]

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

- [ ] Clients still call only `/api/v1`
- [ ] Agent receives bounded authorised context
- [ ] No automatic unsafe generation retry
- [ ] Every invalid result falls back
- [ ] Raw upstream content is not exposed/logged
```
