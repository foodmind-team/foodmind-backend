# Branch 15 — Dashboard Metrics and Weekly Recap

## Branch metadata

- **Branch:** `feat/dashboard-recap`
- **Base dependency:** Branch 14 merged
- **Use case:** UC-09
- **BE item:** BE-029
- **Schema foundation:** use analytics views/functions from V10 committed in
  Branch 01
- **Postman folder:** `14 - Dashboard and Recap`

## Scope

Implement backend-owned, timezone-aware, currency-safe metric definitions for
the personal dashboard and a separate weekly recap projection. Do not persist a
competing DashboardMetric source table.

### Explicit non-scope

- No client-owned metric formula or silent cross-currency conversion.
- No `DashboardMetric` source table or unmeasured materialized view.
- No passive recommendation choice treated as rejection.
- No public/cross-user analytics or personally identifying export.

## Detailed implementation steps

1. Freeze metric definitions and denominators:
   - food/drink count;
   - cuisine distribution;
   - spending total/trend grouped by currency;
   - mean rating;
   - repeated Meal/Place frequency;
   - acceptance/rejection rate and reason distribution;
   - Would Eat Again / Would Buy Again rate;
   - selected candidate type.
2. Define which deleted records/events are excluded and whether records with
   null values contribute to denominators.
3. Derive user only from JWT. Validate date range and IANA timezone; default to
   profile timezone.
4. Use PostgreSQL projections/views/native read queries over source records and
   feedback. No client-specific calculation.
5. Currency:
   - return separate series/totals per currency;
   - do not convert without a frozen conversion source.
6. Return presentation-neutral DTOs with metric code, label, period, value,
   unit/currency, samples/denominator where useful, and explicit empty-state
   metadata.
7. Dashboard accepts bounded current date range and grouping.
8. Weekly recap uses a requested Monday `weekStart`, computes local
   `[start,end)` boundaries, and returns a concise summary projection distinct
   from live dashboard interactions.
9. Optimize with indexes and bounded queries; add materialized data only after
   measured need and an ADR.
10. Build shared fixture expectations consumed by Web Recharts and Android Vico
    parity tests.

## Implementation symbols and query contracts

| Layer | Exact symbol | Required contract |
| --- | --- | --- |
| API | `DashboardController#get` | Accepts bounded `[from,to)`, grouping, and optional IANA timezone; user ID comes only from JWT. |
| API | `WeeklyRecapController#get` | Implements `GET /weekly-recaps/{weekStart}` and rejects a non-Monday or out-of-range local date. |
| Use case | `GetDashboard#handle` | Resolves/validates timezone once, loads every metric for the same UTC boundary pair, and preserves per-currency groups. |
| Use case | `GetWeeklyRecap#handle` | Converts the requested local Monday to one UTC `[start,end)` week and returns an explicit empty projection when no samples exist. |
| Query | `DashboardQueryAdapter#loadMetrics` | Reads V10 views `analytics_consumption_period_v1`, `analytics_spending_period_v1`, `analytics_cuisine_period_v1`, `analytics_repeat_period_v1`, `analytics_recommendation_period_v1`, `analytics_rejection_reason_v1`, and `analytics_candidate_type_selection_v1`, always filtered by `user_id = :actorId` and the requested bounds. |
| Query | `WeeklyRecapQueryAdapter#loadWeek` | Reads `public.analytics_weekly_recap_v1` with actor/week predicates and never recomputes a competing formula in Java. |
| Mapping | `MetricDefinition#map` | Assigns stable metric code, value, unit/currency, sample count, denominator, period, and empty-state semantics; never silently converts currencies. |

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/analytics/api/DashboardController.java
src/main/java/com/foodmind/foodmindbackend/analytics/api/WeeklyRecapController.java
src/main/java/com/foodmind/foodmindbackend/analytics/application/
src/main/java/com/foodmind/foodmindbackend/analytics/domain/MetricDefinition.java
src/main/java/com/foodmind/foodmindbackend/analytics/infrastructure/persistence/
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/analytics/
src/test/resources/fixtures/analytics/
```

## Commit plan

1. `feat(analytics): calculate shared dashboard metrics`
2. `feat(analytics): generate timezone-aware weekly recap`
3. `test(analytics): verify metric definitions and query plans`
4. `docs(api): publish dashboard and recap contracts`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | dashboard metric definitions, query adapters, API DTO/controller, and exact fixture tests under `src/main/java/com/foodmind/foodmindbackend/analytics/` |
| 2 | weekly-recap boundaries/projection/controller and focused timezone tests |
| 3 | remaining currency, denominator, isolation, deletion, N+1, and query-plan evidence under `src/test/java/com/foodmind/foodmindbackend/analytics/` and fixture resources |
| 4 | `src/main/resources/openapi/openapi.yaml` and dashboard/recap examples only |

V10 is an immutable schema foundation. If measured query evidence requires a
change, add the next V12+ forward migration with the query-plan proof.

## Tests

- exact fixture calculation for every metric;
- zero/empty/null denominator;
- user isolation;
- UTC/local day and week boundary;
- Monday week start;
- multiple currencies remain separate;
- deleted records excluded;
- explicit feedback labels only;
- date-range/input bounds;
- representative `EXPLAIN ANALYZE` and no N+1.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*Dashboard*,*WeeklyRecap*,*Analytics*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "14 - Dashboard and Recap"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
fixture results match Web/Android expected values, currency totals stay
separate, and representative query plans use bounded indexed access without
N+1 reads.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `14 - Dashboard and Recap`:

1. Get dashboard for seeded/current date range.
2. Assert metric codes, units, periods, values, and empty metadata.
3. Get weekly recap by Monday.
4. Invalid non-Monday/start range; expect `400`.
5. Multiple-currency fixture; assert separate totals.
6. Secondary user sees only own metrics.
7. Empty user gets `200` with explicit empty state, not fabricated zeros where
   denominator is undefined.

## Pull Request document

**Title**

```text
feat(analytics): add shared dashboard and weekly recap metrics
```

**Body**

```markdown
## Summary

Implements UC-09 backend-owned dashboard metrics and the separate weekly recap
projection for identical Android/Web values.

## Implements

- BE-029
- UC-09

## Metric definitions

[Link/paste metric formula and denominator table.]

## Database/query

- V10 analytics objects: [list]
- query-plan evidence: [link]
- no DashboardMetric source table

## Verification

- analytics fixture/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `14 - Dashboard and Recap`: [PASS]
- Android/Web expected-value fixtures shared: [link]

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

- [ ] Currency totals are not silently combined
- [ ] Timezone/week boundaries are tested
- [ ] Empty denominators are explicit
- [ ] Passive recommendation choices are not rejection
- [ ] Clients do not recalculate metric definitions
```
