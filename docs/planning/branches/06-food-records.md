# Branch 06 — Food Records and Meal Note Projection

## Branch metadata

- **Branch:** `feat/food-records`
- **Base dependency:** Branch 05 merged
- **Use case:** UC-02
- **BE item:** BE-011
- **Database objects:** `media_asset` and `food_record` from V5; generated
  food-record search vector and its GIN/trigram indexes from V6
- **Postman folder:** `05 - Food Records`

## Scope

Implement owner-controlled food record create/read/update/delete, filtering,
soft deletion, optimistic concurrency, optional media reference validation, and
the authorised “Meal Note” projection used later by search and Chatbot.

### Explicit non-scope

- No public/follower feed, hard deletion, or cross-user edit.
- No trusted-group lifecycle; Branch 07 supplies that permission port.
- No storage upload workflow; Branch 16 may enable it later.
- No duplicate `meal_note` or history source table.

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/record/api/FoodRecordController.java
src/main/java/com/foodmind/foodmindbackend/record/api/request/CreateFoodRecordRequest.java
src/main/java/com/foodmind/foodmindbackend/record/api/request/UpdateFoodRecordRequest.java
src/main/java/com/foodmind/foodmindbackend/record/api/response/FoodRecordResponse.java
src/main/java/com/foodmind/foodmindbackend/record/application/CreateFoodRecord.java
src/main/java/com/foodmind/foodmindbackend/record/application/GetFoodRecord.java
src/main/java/com/foodmind/foodmindbackend/record/application/ListFoodRecords.java
src/main/java/com/foodmind/foodmindbackend/record/application/UpdateFoodRecord.java
src/main/java/com/foodmind/foodmindbackend/record/application/DeleteFoodRecord.java
src/main/java/com/foodmind/foodmindbackend/record/application/port/FoodRecordQuery.java
src/main/java/com/foodmind/foodmindbackend/record/domain/
src/main/java/com/foodmind/foodmindbackend/record/infrastructure/persistence/
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/record/
```

## Detailed implementation steps

1. Map `food_record` exactly; keep persisted record, DTO, and Meal Note
   projection separate.
2. Create:
   - derive `owner_user_id` from the JWT;
   - resolve optional Meal/Place/Cuisine IDs;
   - require snapshot names so old records remain meaningful if catalogue data
     is retired;
   - validate timestamp, money/currency, rating, comment length, Would Eat
     Again, and visibility combination;
   - validate optional media ownership/readiness.
3. Initially support `PRIVATE` creation. `GROUP` creation must call the explicit
   group-membership permission port introduced in Branch 07; until then reject
   or keep the contract disabled rather than bypass permission.
4. Detail read uses one permission-scoped query:
   owner OR (`GROUP` and active member). Do not fetch first and filter later.
5. List endpoint filters date range, cuisine, meal, place, visibility, group,
   rating, and sort. Enforce maximum page size and stable secondary sort by ID.
6. Update:
   - owner only;
   - check optimistic version/`If-Match` contract;
   - re-run visibility/media validation;
   - preserve immutable owner/creation data.
7. Delete is an owner-only soft delete. Repeated delete is idempotent according
   to the frozen API decision. Deleted rows never appear in normal queries.
8. Define `MealNoteView` with only safe authorised fields used by search/chat.
   Do not add a `meal_note` table.
9. Map database uniqueness/check failures to stable API errors.
10. Ensure repository projections avoid loading comments/media when list cards
    do not need them.

## Tests

- create all valid field combinations;
- invalid money/rating/time/comment/visibility/media;
- owner read/update/delete;
- another user receives non-disclosing `404`;
- deleted record is unavailable;
- optimistic conflict;
- pagination/filter/sort stability;
- Meal Note projection contains no owner-private fields not in contract;
- SQL count/N+1 assertion for representative list query.

## Commit plan

1. `feat(record): create and retrieve food records`
2. `feat(record): update delete and filter food history`
3. `feat(record): expose authorised Meal Note projection`
4. `test(record): cover ownership validation and soft deletion`
5. `docs(api): publish food record contract`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | create/get domain, API, application, persistence mapping/query code and focused tests under `src/main/java/com/foodmind/foodmindbackend/record/` |
| 2 | patch/delete/list filters, optimistic locking, soft-delete code and focused tests under the same feature |
| 3 | `MealNoteView`, its application port/projection adapter, and focused authorization tests |
| 4 | remaining owner/non-owner, invalid input, pagination, N+1, and deletion matrix under `src/test/java/com/foodmind/foodmindbackend/record/` |
| 5 | `src/main/resources/openapi/openapi.yaml` and food-record examples only |

Use V5 as the immutable schema foundation. If a mapping correction is
necessary, introduce the next V12+ forward migration with the affected code.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*FoodRecord*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "05 - Food Records"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
owner/group/private/soft-delete cases are non-disclosing and optimistic
conflicts return the frozen API error.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `05 - Food Records`:

1. Create private record; store `foodRecordId` and version/ETag.
2. Get by ID.
3. List/filter by date and cuisine.
4. Update rating/comment/Would Eat Again.
5. Secondary user tries to read/update; expect non-disclosing `404`.
6. Invalid rating and visibility/group combination; expect `400`.
7. Delete as owner.
8. Get deleted record; expect `404`.

## Pull Request document

**Title**

```text
feat(record): implement secure food records and Meal Note view
```

**Body**

```markdown
## Summary

Implements UC-02 food record CRUD/history foundations with ownership-scoped
queries, soft deletion, and the canonical Meal Note projection.

## Implements

- BE-011
- UC-02 food-record portion

## Database

- Objects used: `media_asset`, `food_record`
- Migration/forward fix: [file or "provided by schema foundation"]
- empty and upgrade tests: [PASS]

## Permissions

[Describe owner, Group placeholder/integration, non-disclosure, and delete rules.]

## API

[List endpoints, filters, versioning and OpenAPI commit.]

## Verification

- record/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `05 - Food Records`: [PASS]

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

- [ ] Owner comes only from JWT
- [ ] Permission is part of the query
- [ ] Deleted records are excluded everywhere
- [ ] No `meal_note` duplicate table
- [ ] List query has no N+1 regression
```
