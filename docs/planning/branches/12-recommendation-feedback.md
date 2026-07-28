# Branch 12 — Recommendation Feedback and ML Snapshot Export

## Branch metadata

- **Branch:** `feat/recommendation-feedback`
- **Base dependency:** Branch 11 merged
- **Use case:** UC-05
- **BE items:** BE-025, BE-025A
- **Schema foundation:** use `recommendation_feedback` from V7 and the
  restricted ML export source from V10, committed in Branch 01
- **Postman folder:** `11 - Recommendation Feedback`

## Scope

Implement immutable, idempotent recommendation feedback events; separate
acceptance labels from collaborative strength; connect later consumption;
support true re-recommendation as a new linked session; and provide a restricted
pseudonymised offline-ML snapshot export.

### Explicit non-scope

- No passive non-selection label and no silent event overwrite.
- No public/client ML export endpoint.
- No model training, evaluation, or inference implementation.
- No direct identifiers, comments, Chat content, tokens, or exact location in
  an export.

## Detailed implementation steps

1. Freeze event DTOs:
   `ACCEPTED`, `REJECTED`, `RERECOMMEND_REQUESTED`, `LATER_RATED`,
   `WOULD_EAT_AGAIN`.
2. Validate session owner and candidate membership. Candidate may be null only
   for event types explicitly defined as session-level.
3. Apply event-specific checks:
   - acceptance/rejection require returned candidate;
   - rejection reason allow-listed;
   - rating in range;
   - boolean only for Would Eat Again;
   - optional resulting FoodRecord belongs to user and matches candidate
     context sufficiently for the approved contract.
4. Store events append-only. Never overwrite an earlier event silently.
5. Define conflict/correction policy:
   - duplicate retry with same idempotency key returns original event;
   - key/payload mismatch conflicts;
   - contradictory terminal decision is rejected or appended as an explicit
     correction event if that event is added to the frozen contract.
6. `RERECOMMEND_REQUESTED` records an event and the subsequent generate request
   creates a new session with `parentSessionId`. Do not mutate/rerank the old
   session.
7. Temporary rejection constraints are derived from reason plus
   `effective_until`; define which reasons create constraints and their bounded
   duration.
8. Analytics semantics:
   - accepted = supervised label `1`;
   - explicitly rejected = label `0`;
   - no event/passive non-selection = no label;
   - later rating/Would Eat Again may affect collaborative strength but do not
     retroactively invent a missing supervised decision.
9. Implement restricted export as an operator CLI/application service, not a
   public client endpoint.
10. Treat `ml_interaction_export_source_v1` as a restricted raw source. For
    each exact `feature_schema_version`, `TrainingFeatureSchemaRegistry`
    defines the permitted output keys, JSON pointers, scalar types, null/default
    rules, and numeric/category bounds. Reject an unknown schema version,
    missing required key, unexpected top-level or nested key, wrong type, or
    out-of-range value; construct a new typed output object and never redact or
    pass through `raw_feature_snapshot`.
11. Read it only through
    `foodmind_ml_interaction_export_rows_v1(decisionFrom, decisionTo,
    observedThrough)` inside a repeatable-read export. Record the half-open
    decision window, exclusive later-signal observation cutoff, and selected
    signal timestamps in the manifest.
12. Export only allow-listed columns: domain-separated HMAC keys for
    `user:`, `meal:`, and `offering:`, event time, label, bounded later signals,
    validated point-in-time features, feature schema, rank/type, and
    model/fallback metadata. Rows with a null feature pair may enter only the
    declared collaborative output and are counted as excluded from the LR
    feature dataset.
13. Exclude source `user_id`, decision/session/candidate/meal/offering row IDs, email/display
    name, comments, chat, exact location, raw group-member rows, tokens, passive
    negatives, and the complete raw feature JSON. Test nested forbidden keys
    such as `context.location.latitude`, `email`, `userId`, and `comment`.
14. Emit manifest with Backend commit, schema version, feature allow-list
    version/checksum, time range, row count,
    content checksum, and synthetic/real provenance classification.

## Implementation symbols and query contracts

| Layer | Exact symbol | Required contract |
| --- | --- | --- |
| API | `RecommendationFeedbackController#submit` | Implements `POST /recommendations/{sessionId}/feedback`; derives the actor from JWT and requires `Idempotency-Key`. |
| Use case | `SubmitFeedback#handle` | Loads the owner-scoped session/candidate, invokes `FeedbackPolicy`, canonicalizes the request hash, and appends exactly one event. |
| Policy | `FeedbackPolicy#validatePayload`, `deriveTemporaryConstraint`, `labelFor` | Enforces the event-specific field matrix and maps only `ACCEPTED`/`REJECTED` to supervised labels. |
| Repository | `RecommendationFeedbackRepository#insertOrResolveRetry` | Inserts under `UNIQUE (user_id, idempotency_key)`; same canonical payload returns the existing event and a different payload yields conflict without update/delete. |
| Re-recommend | `SubmitFeedback#linkReRecommendation` | Records `RERECOMMEND_REQUESTED`; the later generation creates a distinct session with the same-owner `parent_session_id`. |
| Export | `BuildTrainingSnapshot#handle`, `TrainingFeatureSchemaRegistry#require`, `TrainingSnapshotWriter#write` | Calls `public.foodmind_ml_interaction_export_rows_v1(:decisionFrom, :decisionTo, :observedThrough)` in repeatable read, rejects unknown/raw feature keys, builds a new typed allow-listed object, derives domain-separated user/Meal/offering HMAC keys, drops every raw ID, and writes window/cutoff/signal-time/checksum/count/schema/provenance metadata. |
| Operator entry | `TrainingSnapshotCommand#run` | Is an authenticated operator/CLI path, not an MVC controller and not part of public OpenAPI. |

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/recommendation/api/RecommendationFeedbackController.java
src/main/java/com/foodmind/foodmindbackend/recommendation/application/SubmitFeedback.java
src/main/java/com/foodmind/foodmindbackend/recommendation/application/BuildTrainingSnapshot.java
src/main/java/com/foodmind/foodmindbackend/recommendation/domain/FeedbackPolicy.java
src/main/java/com/foodmind/foodmindbackend/recommendation/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/recommendation/infrastructure/export/
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/recommendation/RecommendationFeedbackControllerTest.java
src/test/java/com/foodmind/foodmindbackend/recommendation/TrainingSnapshotExportTest.java
```

## Commit plan

1. `feat(recommendation): record explicit feedback events`
2. `feat(recommendation): link re-recommendation sessions`
3. `feat(recommendation): export pseudonymised ML snapshots`
4. `test(recommendation): protect feedback label semantics`
5. `docs(api): publish feedback and re-recommend contracts`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | feedback DTO/controller/use case/policy/persistence plus focused event/idempotency tests |
| 2 | parent-session/re-recommend orchestration and immutable-old-session tests |
| 3 | restricted export application/infrastructure code, manifest/schema fixtures, and privacy tests |
| 4 | remaining contradiction, concurrent retry, label semantics, pseudonym, and checksum matrix |
| 5 | `src/main/resources/openapi/openapi.yaml` and public feedback/re-recommend examples only; the restricted export is not public OpenAPI |

V7/V10 are immutable. Add a V12+ forward migration only for an approved
correction, with label/export compatibility evidence in the same PR.

## Tests

- each event type valid/invalid payload;
- session/candidate/record ownership;
- duplicate/concurrent idempotent submission;
- contradictory events;
- parent/child session link;
- expiry of temporary constraints;
- export contains explicit labels only;
- passive candidates absent from labels;
- pseudonym stability within snapshot and no direct identifiers;
- unknown schema, unexpected/nested forbidden key, wrong type, and out-of-range
  raw feature all fail closed before any artifact is published;
- fixed decision/observation bounds reproduce the same selected later signals;
- null feature pairs are counted and excluded from the LR feature dataset;
- manifest checksum/row count.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*Feedback*,*TrainingSnapshot*,*Idempotency*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "11 - Recommendation Feedback"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
duplicate retries do not add rows, append-only mutation attempts fail, and the
export fixture/manifest proves there are no passive labels or direct
identifiers.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `11 - Recommendation Feedback`:

1. Accept lead candidate; store `feedbackId`.
2. Retry same key; assert same event.
3. Reject another candidate with reason.
4. Submit invalid rating; expect `400`.
5. Submit later rating/Would Eat Again linked to user's record.
6. Secondary user submits to primary session; expect `404`.
7. Submit `RERECOMMEND_REQUESTED`, generate with parent ID, and assert new
   session ID.
8. Verify old session order remains unchanged.

ML export is tested through restricted integration/CLI tests, not public
Postman.

## Pull Request document

**Title**

```text
feat(recommendation): add explicit feedback and ML snapshot export
```

**Body**

```markdown
## Summary

Implements UC-05 immutable feedback, linked re-recommendation, later signals,
and a privacy-scoped offline-ML export.

## Implements

- BE-025, BE-025A
- UC-05

## Label semantics

- acceptance: 1
- explicit rejection: 0
- passive non-selection: absent/unknown
- collaborative-strength mapping: [describe]

## Privacy/export

[List included/excluded fields, pseudonym method, manifest schema, checksum.]

## Database / forward fix

- Uses immutable V7 feedback events and V10 ML export-source view.
- New migration: [None / V12+ filename and reason]
- Label/export compatibility and rollback impact: [describe]

## Verification

- feedback/idempotency/export tests: [PASS]
- full Maven tests: [PASS]
- Postman `11 - Recommendation Feedback`: [PASS]

## Cross-repository action

- ML dataset schema/version: [link]
- training snapshot checksum: [test fixture]

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

- [ ] Events are append-only
- [ ] Old session is not reranked
- [ ] Passive choices never become negative labels
- [ ] Export contains no direct identity/chat/comment data
- [ ] Snapshot metadata is reproducible
```
