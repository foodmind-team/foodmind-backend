# Branch 13 — Cooking Plan Orchestration

## Branch metadata

- **Branch:** `feat/cooking-plan`
- **Base dependency:** Branch 12 merged
- **Use case:** UC-06
- **BE item:** BE-026
- **Schema foundation:** use cooking objects from V8 and central idempotency
  from V10, committed in Branch 01
- **Postman folder:** `12 - Cooking Plans`

## Scope

Implement manual/authorised ingredient input, time/budget/serving/dietary
validation, controlled-recipe context, independent Cooking Agent orchestration,
structured output validation, persistence snapshots, and explicit
fallback/unavailable results.

### Explicit non-scope

- No automatic pantry/inventory capture, expiry inference, or purchasing.
- No arbitrary recipe invention outside the controlled candidate set.
- No recommendation or Chatbot route through the Cooking endpoint.
- No remote call inside a database transaction.

## Detailed implementation steps

1. Freeze request DTO: ordered ingredient inputs, quantity/unit when known,
   servings, max minutes, max budget/currency, and dietary rules.
2. Do not imply automatic pantry detection, inventory accuracy, expiry
   detection, purchasing, or recipe invention.
3. Resolve authenticated user's hard dietary/allergy rules and merge only
   permitted request constraints.
4. Retrieve a bounded controlled recipe candidate set or expose the approved
   narrow recipe tool. Every selected source recipe must exist and be active.
5. In TX1 insert `cooking_plan` as `CREATED`, append every ordered input while
   the parent is `CREATED`, update it to `PROCESSING`, then commit before the
   Agent call.
6. Invoke the dedicated Cooking Agent contract. It must not call
   recommendation/inference or Chatbot workflows.
7. Validate:
   - matching request/trace/contract version;
   - source recipe ID in authorised controlled candidate set;
   - ingredient and step counts/order/length;
   - servings/time/budget and dietary compatibility;
   - allow-listed warning codes;
   - no unsupported food-safety claim.
8. In TX2 lock the `PROCESSING` parent, append all validated output
   ingredients/steps/warnings, verify success has at least one ingredient and
   step, then update source recipe, contract/fallback version/status, failure
   metadata, and terminal status last.
9. Failure handling:
   - constraint conflict;
   - no controlled recipe match;
   - Agent timeout/unavailable;
   - malformed/invalid output.
   Return a structured state, never raw Agent text.
10. Require idempotency for generation. Same key/payload returns the same plan.
11. Owner-only get/list; stable historical snapshot even when recipe catalogue
    changes.
12. Mark stale `PROCESSING` plans using the shared recovery mechanism.

## Implementation symbols and query contracts

| Layer | Exact symbol | Required contract |
| --- | --- | --- |
| API | `CookingPlanController#generate`, `get`, `list` | Implements the frozen generate/detail/history endpoints; actor identity comes only from JWT. |
| Use case | `GenerateCookingPlan#handle` | Validates request/hard user constraints and delegates idempotent TX1 → remote call → validated TX2 orchestration. |
| Use case | `GetCookingPlan#handle`, `ListCookingPlans#handle` | Returns owner-scoped immutable snapshots; list uses bounded seek pagination. |
| Transaction | `CookingTransactionService#createProcessingPlan`, `completePlan`, `markFailed` | TX1 performs `CREATED` parent -> inputs -> `PROCESSING`; TX2 locks `PROCESSING`, inserts validated outputs, checks required output cardinality, then terminalises last. This exact order satisfies V8's parent/child guards. |
| Port | `CookingAgentPort#generate`; `CookingAgentHttpAdapter#generate` | Exchanges only versioned controlled-recipe candidates, enforces deadline/payload limits, and never calls recommendation or Chat ports. |
| Validation | `CookingPlanResultValidator#validate` | Verifies trace/version, selected active candidate ID, order/cardinality, dietary/allergen, servings/time/budget, warning allow-list, and text bounds. |
| Query | `CookingPlanQueryAdapter#findOwned`, `findOwnedPage` | Includes `user_id = :actorId`; history seeks on `(created_at DESC, id DESC)` and loads ordered child snapshots without N+1 queries. |
| Idempotency | `IdempotencyService#execute` with operation `COOKING_PLAN_GENERATE` | Same key/request hash returns the existing plan; same key/different hash conflicts under V10 uniqueness. |

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/cooking/api/
src/main/java/com/foodmind/foodmindbackend/cooking/application/
src/main/java/com/foodmind/foodmindbackend/cooking/application/port/CookingAgentPort.java
src/main/java/com/foodmind/foodmindbackend/cooking/domain/
src/main/java/com/foodmind/foodmindbackend/cooking/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/integration/agent/CookingAgentHttpAdapter.java
src/main/resources/openapi/openapi.yaml
src/test/resources/contracts/agent/cooking/
src/test/java/com/foodmind/foodmindbackend/cooking/
```

## Commit plan

1. `docs(api): freeze Cooking Agent contract fixtures`
2. `feat(cooking): validate and persist cooking requests`
3. `feat(cooking): orchestrate controlled Cooking Agent`
4. `feat(cooking): validate and snapshot structured plans`
5. `test(cooking): cover constraints timeouts and ownership`
6. `docs(api): publish cooking plan contract`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | versioned Cooking fixtures under `src/test/resources/contracts/agent/cooking/` and contract notes |
| 2 | Cooking domain/request validation, TX1 persistence/idempotency, API skeleton, and focused tests |
| 3 | `CookingAgentPort`, integration DTO/client configuration/adapter, timeout handling, and contract tests |
| 4 | result validation, TX2 snapshot persistence/retrieval/history, fallback/status mapping, and focused tests |
| 5 | remaining ownership, conflict, no-match, timeout, ordering, and transaction-boundary tests |
| 6 | `src/main/resources/openapi/openapi.yaml` and Cooking examples only |

V8 and V10 are immutable foundations. Use the next V12+ forward migration only
for an implementation-proven correction.

## Tests

- normal request and deterministic ordering;
- allergies/diet conflict;
- budget/time/serving/input bounds;
- no recipe match;
- timeout/unavailable/invalid recipe/steps/warnings;
- idempotency conflict and concurrent retry;
- owner isolation;
- old plan stable after recipe update;
- request mutation, terminal reversal, parent/child deletion, child update,
  late input, and post-terminal output insertion are rejected by V8;
- no call to recommendation/inference/Chatbot ports.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*CookingPlan*,*CookingAgent*,*Idempotency*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "12 - Cooking Plans"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
the delayed Agent stub observes no open transaction, invalid controlled-recipe
output is rejected, and an old plan remains byte-for-byte stable after catalogue
updates.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `12 - Cooking Plans`:

1. Generate normal plan; store `cookingPlanId`.
2. Get/list plan and assert ordered steps/source recipe.
3. Repeat idempotency key; assert same ID.
4. Submit allergy conflict; assert structured conflict/no-match.
5. Submit no-match ingredients.
6. Exercise Agent timeout fixture; assert safe unavailable/fallback state.
7. Secondary user gets primary plan; expect `404`.

## Pull Request document

**Title**

```text
feat(cooking): implement controlled Cooking Plan workflow
```

**Body**

```markdown
## Summary

Implements the independent UC-06 Cooking workflow using controlled recipes,
structured Agent output, snapshot persistence, and safe failures.

## Implements

- BE-026
- UC-06

## Database

- `V8__cooking.sql`
- V10 central idempotency records
- ordered input/output/step/warning snapshots
- migration tests: [PASS]
- New migration/forward fix: [None / V12+ filename, reason, rollback impact]

## Contracts

- public OpenAPI version: [value]
- Cooking Agent contract version: [value]

## Verification

- Cooking contract/security/failure tests: [PASS]
- full Maven tests: [PASS]
- Postman `12 - Cooking Plans`: [PASS]

## Non-scope

- no automatic inventory capture
- no purchasing
- no arbitrary recipe invention
- no recommendation or Chatbot routing

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

- [ ] Remote call is outside DB transaction
- [ ] Source recipe is controlled and validated
- [ ] Output is immutable snapshot
- [ ] Allergies/diet constraints cannot be overridden
- [ ] Historical plan remains stable
```
