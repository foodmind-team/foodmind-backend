# Branch 10 — Recommendation Context, Hard Rules, and Fallback

## Branch metadata

- **Branch:** `feat/recommendation-fallback`
- **Base dependency:** Branch 09 merged
- **Use case:** UC-04
- **BE items:** BE-018, BE-019, BE-020, BE-021
- **Schema foundation:** use recommendation objects from V7 and central
  idempotency from V10, committed in Branch 01
- **Postman folder:** `09 - Recommendation Fallback`

## Scope

Deliver the first complete recommendation vertical slice without depending on
Intelligence: authorised context, candidate retrieval, hard filters,
session/evidence persistence, idempotency, deterministic Personal/Exploratory/
Group-inspired selection, grounded reason templates, and ordered public
response.

### Explicit non-scope

- No Agent/model call; Branch 11 integrates it behind this contract.
- No feedback labels or passive-negative inference; Branch 12 owns feedback.
- No Cooking/Chat routing or public-internet candidate retrieval.
- No server mutation for the client-only `Try another` spotlight action.

## Detailed implementation steps

1. Freeze request DTO: optional group, meal type, budget/currency, area or
   coordinate/distance, mood, requested time, and supported temporary
   constraints. Never accept raw group evidence from the client.
2. Resolve authenticated user preferences, own history, Want to Try, current
   active group evidence, and active curated `place_meal` candidates through
   bounded read ports.
3. Deduplicate by `place_meal_id`; create immutable evidence objects with
   source IDs, timestamps, and aggregate values.
4. Implement independent hard-filter policies:
   - allergen;
   - required dietary tag;
   - budget/currency;
   - spice;
   - disliked cuisine;
   - recent-repeat window;
   - area/distance;
   - requested time/availability;
   - supported cleanliness-evidence threshold.
5. Each policy returns an allow result or stable filter code. Missing evidence
   is not silently treated as proof of safety/cleanliness.
6. Idempotency:
   - require `Idempotency-Key`;
   - hash canonical request;
   - same key/same hash returns the existing result;
   - same key/different hash returns conflict;
   - handle concurrent inserts transactionally.
7. TX1 creates the session as `CREATED`, inserts candidate evaluation rows,
   moves the session to `PROCESSING`, and commits before any future remote call.
   Store request/evidence snapshots and filter codes.
8. Fallback v1 selection:
   - Personal: strongest personal/preferences evidence;
   - Exploratory: valid, non-recent novelty;
   - Group-inspired: strongest active-group evidence;
   - exclude duplicate offering across types;
   - deterministic tie-break by relevant evidence, budget/distance fit, then ID.
9. Return fewer than three only when evidence/candidate supply cannot safely
   fill every type. Never fabricate a card.
10. Generate explanation text from an allow-listed template map backed by
    candidate reasons; do not expose score as explanation.
11. TX2 locks the `PROCESSING` session, inserts reasons while candidates remain
    `ELIGIBLE`, advances returned candidates/ranks, then makes the terminal
    session update last. Store `fallbackVersion=fallback-v1` and completion
    time.
12. Handle no valid candidate as `NO_VALID_CANDIDATE` with a successful,
    presentation-safe empty result—not a raw server error.
13. `GET /recommendations/{sessionId}` is owner-only and returns the same stable
    ordered set.
14. `GET /recommendations/history` returns only the authenticated user's
    sessions with bounded pagination and a stable `(createdAt,id)` order. It
    uses a list projection and does not hydrate full evidence JSON.
15. Publish versioned public recommendation fixtures under
    `src/test/resources/fixtures/public/recommendation/`. Web and Android must
    each run a consumer test against the same fixture checksum before BE-021 is
    complete.
16. `Try another` requires no endpoint and must not mutate the session.

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/recommendation/api/
src/main/java/com/foodmind/foodmindbackend/recommendation/application/GenerateRecommendation.java
src/main/java/com/foodmind/foodmindbackend/recommendation/application/GetRecommendation.java
src/main/java/com/foodmind/foodmindbackend/recommendation/application/port/
src/main/java/com/foodmind/foodmindbackend/recommendation/domain/filter/
src/main/java/com/foodmind/foodmindbackend/recommendation/domain/fallback/
src/main/java/com/foodmind/foodmindbackend/recommendation/domain/reason/
src/main/java/com/foodmind/foodmindbackend/recommendation/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/common/idempotency/
src/main/resources/openapi/openapi.yaml
src/test/resources/fixtures/public/recommendation/
src/test/java/com/foodmind/foodmindbackend/recommendation/
```

## Tests

- every hard filter independently and in combination;
- missing evidence/cold start;
- private and inactive-group evidence exclusion;
- deterministic repeated output;
- Personal/Exploratory/Group-inspired diversity;
- fewer-than-three and no-result cases;
- reason-code/evidence consistency;
- idempotent retry, payload conflict, concurrent retry;
- session owner isolation;
- `Try another` absent from server mutation contract.

## Commit plan

1. `feat(recommendation): build authorised candidate context`
2. `feat(recommendation): apply deterministic hard constraints`
3. `feat(recommendation): persist idempotent generation sessions`
4. `feat(recommendation): rank diverse fallback candidates`
5. `test(recommendation): cover permissions filters and fallback`
6. `docs(api): publish recommendation generation contract`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | context read ports/assemblers and authorised-query adapters under `src/main/java/com/foodmind/foodmindbackend/recommendation/` with focused tests |
| 2 | domain hard-filter policies/reason codes and their unit tests |
| 3 | session/candidate/reason persistence, idempotency application support, generation/get controllers, and integration tests |
| 4 | fallback selector/template reasons and deterministic/diversity tests |
| 5 | full permission, filter-combination, concurrency, empty-result, and stable-order matrix under `src/test/java/com/foodmind/foodmindbackend/recommendation/` |
| 6 | `src/main/resources/openapi/openapi.yaml`, `src/test/resources/fixtures/public/recommendation/`, fixture checksum manifest, and documented Web/Android consumer evidence |

V7 and V10 are immutable foundations. Add a V12+ forward migration with the
first dependent implementation commit only if a correction is unavoidable.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*Recommendation*,*Idempotency*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "09 - Recommendation Fallback"
```

Expected: both Maven commands exit `0` and Newman reports zero failed
assertions. Run with the Agent service stopped and verify the feature remains
complete, deterministic, and idempotent.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `09 - Recommendation Fallback`:

1. Generate with personal context; store `recommendationSessionId` and first
   `candidateId`.
2. Assert ranks contiguous, max three, IDs unique, reasons present.
3. Generate with authorised group; assert Group-inspired when fixture permits.
4. Retry same idempotency key/payload; assert same session.
5. Reuse key with changed payload; expect conflict.
6. Submit allergy/budget constraint eliminating candidates; assert none violate.
7. No-valid-candidate fixture; expect explicit status/empty items.
8. Secondary user gets primary session; expect `404`.
9. Get session twice; assert stable candidate order.
10. List recommendation history; assert owner-only stable pagination.
11. Share the returned lead candidate to the active group after a candidate
    exists; this executes the BE-015 endpoint that cannot succeed in folder 06
    before Branch 10 has created a candidate.

## Pull Request document

**Title**

```text
feat(recommendation): deliver authorised deterministic fallback
```

**Body**

```markdown
## Summary

Delivers the P0 end-to-end recommendation path using authorised data, hard
rules, idempotent persistence, and deterministic diverse fallback. Intelligence
can be added later without changing the public contract.

## Implements

- BE-018, BE-019, BE-020, BE-021
- UC-04 baseline

## Database

- `V7__recommendations.sql`
- V7 session/candidate/reason objects
- V10 central idempotency object
- migration tests: [PASS]

## Rules and permissions

[List hard filters, evidence sources, group checks, and fallback v1 policy.]

## API

[Link OpenAPI and examples. Confirm Try another has no endpoint.]

## Verification

- recommendation/idempotency/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `09 - Recommendation Fallback`: [PASS]
- Agent unavailable demonstration: [PASS]

## Cross-repository action

- shared fixture version/checksum: [value]
- Web consumer test: [PASS + link]
- Android consumer test: [PASS + link]

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

- [ ] No inactive/private group evidence
- [ ] No remote call inside a DB transaction
- [ ] No score presented as explanation
- [ ] Passive non-selection is not feedback
- [ ] Same idempotency request returns same session
- [ ] Candidate order is stable
```
