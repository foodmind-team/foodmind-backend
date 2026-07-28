# Branch 08 — Drink Records and Combined History

## Branch metadata

- **Branch:** `feat/drink-records-history`
- **Base dependency:** Branch 07 merged
- **Use case:** UC-02
- **BE item:** BE-012
- **Schema foundation:** use `drink_record` from V5 committed in Branch 01
- **Postman folder:** `07 - Drink Records and History`

## Scope

Implement drink record CRUD with sweetness, ice, and Would Buy Again fields,
then provide one presentation-neutral combined food/drink history endpoint for
daily, weekly, and monthly client views.

### Explicit non-scope

- No duplicate history table or dashboard metric calculation.
- No inventory, purchasing, or nutrition inference.
- No public visibility or permission rules different from food records.
- No client-owned timezone bucketing formula.

## Detailed implementation steps

1. Reuse shared ownership, visibility, money, rating, timestamp, media, and
   pagination concepts without creating a generic catch-all JPA entity.
2. Validate drink name, optional Place/shop snapshot, sweetness, ice, price,
   rating, comment, Would Buy Again, and `PRIVATE`/`GROUP` combination.
3. Use the same query-level permission model as food records.
4. Implement owner-only update and soft delete with optimistic concurrency.
5. Build `GET /history` as a read projection/union, not a second history table.
6. Inputs:
   - `from`/`to` inclusive/exclusive contract;
   - `period=DAY|WEEK|MONTH`;
   - `types=FOOD,DRINK`;
   - optional group/cuisine/place filters;
   - user's timezone or validated explicit timezone.
7. Convert UTC event timestamps into local bucket dates in PostgreSQL or one
   shared backend service. Define week start as Monday.
8. Return stable chronological entries or buckets with source type and opaque
   source ID. Do not duplicate full record details unnecessarily.
9. Reject ranges exceeding the documented maximum.
10. Add indexes/query-plan verification for owner/group time-range queries.

## Implementation symbols and query contracts

| Layer | Exact symbol | Required contract |
| --- | --- | --- |
| API | `DrinkRecordController#create`, `list`, `get`, `update`, `delete` | Implements the complete `/drink-records` surface; the authenticated principal is the only owner input. |
| API | `HistoryController#getHistory` | Parses `from`, exclusive `to`, `period`, `types`, timezone, filters, and cursor; rejects unsupported ranges before querying. |
| Use case | `CreateDrinkRecord#handle`, `ListDrinkRecords#handle`, `GetDrinkRecord#handle`, `UpdateDrinkRecord#handle`, `DeleteDrinkRecord#handle` | Applies the same ownership/group visibility policy as food records and returns projections rather than persistence entities. |
| Use case | `GetHistory#handle` | Converts the requested local range to UTC once, delegates one authorised query, and returns a presentation-neutral page/bucket result. |
| Query | `DrinkRecordQueryAdapter#findVisibleById` | Includes `(owner_user_id = :actorId OR active group membership)` and `deleted_at IS NULL` in SQL; a denied row is indistinguishable from a missing row. |
| Query | `HistoryQueryAdapter#findAuthorisedHistory` | Uses `UNION ALL` over food/drink projections, with the permission and soft-delete predicate inside each branch before filters, ordering, and limit. |
| Ordering | `HistoryCursor#after` | Uses `(occurred_at DESC, source_type ASC, source_id DESC)` for entries; buckets use local bucket start ascending and never depend on JVM default timezone. |

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/record/api/DrinkRecordController.java
src/main/java/com/foodmind/foodmindbackend/record/api/HistoryController.java
src/main/java/com/foodmind/foodmindbackend/record/application/
src/main/java/com/foodmind/foodmindbackend/record/application/GetHistory.java
src/main/java/com/foodmind/foodmindbackend/record/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/record/infrastructure/persistence/HistoryQueryAdapter.java
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/record/DrinkRecordControllerTest.java
src/test/java/com/foodmind/foodmindbackend/record/HistoryQueryAdapterTest.java
```

## Commit plan

1. `feat(record): create and manage drink records`
2. `feat(record): query combined timezone-aware history`
3. `test(record): cover drink visibility and history buckets`
4. `docs(api): publish drink and history contracts`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | drink domain/application/API/persistence code and focused CRUD tests under `src/main/java/com/foodmind/foodmindbackend/record/` |
| 2 | history controller/use case/projection query and timezone/bucket tests |
| 3 | remaining drink/group/owner/deletion and combined-history matrix under `src/test/java/com/foodmind/foodmindbackend/record/` |
| 4 | `src/main/resources/openapi/openapi.yaml` and drink/history examples only |

Never edit V5. Add the next V12+ forward migration only for an
implementation-proven mapping or measured-index correction.

## Tests

- drink field boundary values;
- owner/group/non-member permission matrix;
- soft deletion and optimistic conflict;
- UTC events around Asia/Singapore day/week boundaries;
- Monday week start and month boundaries;
- food-only, drink-only, combined, empty history;
- maximum range/page size;
- no duplicate/missing entries in union.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*DrinkRecord*,*History*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "07 - Drink Records and History"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
timezone-boundary fixtures contain neither duplicate nor missing records and
denied/private records remain non-disclosing.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `07 - Drink Records and History`:

1. Create private and group-visible drinks; store `drinkRecordId`.
2. Get/update each.
3. List drink records with bounded date/pagination filters.
4. Secondary group member reads group item but not private item.
5. Query DAY/WEEK/MONTH combined history.
6. Query food-only and drink-only.
7. Invalid date range/enum expects `400`.
8. Delete and verify history exclusion.

## Pull Request document

**Title**

```text
feat(record): add drink records and combined history
```

**Body**

```markdown
## Summary

Completes UC-02 record coverage with drinks and a shared timezone-aware
food/drink history projection.

## Implements

- BE-012
- remaining UC-02 scope

## Metric/time definitions

- timezone: [rule]
- week start: Monday
- date range semantics: [describe]

## Database/query

- `drink_record`
- history union/projection: [implementation]
- representative query plan: [link/evidence]

## Verification

- drink/history/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `07 - Drink Records and History`: [PASS]

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

- [ ] No duplicate history table
- [ ] UTC/local boundaries tested
- [ ] Group permission is query-scoped
- [ ] Deleted records are excluded
- [ ] Android/Web receive identical buckets
```
