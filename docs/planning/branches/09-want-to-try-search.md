# Branch 09 — Want to Try, Authorised Search, and Explore

## Branch metadata

- **Branch:** `feat/want-to-try-search`
- **Base dependency:** Branch 08 merged
- **Use cases:** UC-03, UC-07 foundation
- **BE items:** BE-016, BE-017
- **Schema foundation:** use `V6__saved_search_and_explore.sql` committed in
  Branch 01; do not edit it in this branch
- **Postman folder:** `08 - Saved, Search and Explore`

## Scope

Implement user-owned Want to Try references, permission-scoped PostgreSQL
full-text/trigram search, and the Explore read model composed only of currently
authorised group-visible records and curated catalogue content.

### Explicit non-scope

- No public/follower feed or unauthorised cross-group discovery.
- No public-internet, map, restaurant, or product-provider search.
- No persisted copy of content that bypasses live source authorization.
- No search document type beyond FoodRecord, FoodProduct, and Place in V6.

## Detailed implementation steps

1. Map `want_to_try` with exactly one supported source:
   FoodRecord, Meal, FoodProduct, or Place.
2. On save, resolve and authorise the source before insert. Enforce active
   partial uniqueness and translate duplicates into idempotent success or a
   stable conflict according to OpenAPI.
3. On list, each saved row is owner-only and its source is authorised again.
   If access was revoked, return an explicit unavailable source state without
   leaking its old content.
4. Delete is owner-only soft delete.
5. Implement search branches:
   - owner's non-deleted FoodRecords;
   - group-visible FoodRecords for current active memberships;
   - active curated FoodProducts and Places.
6. Call V6 with the authenticated actor, bounded query/source types, page size,
   and all-or-none keyset cursor. Permission predicates, matching, ranking,
   global ordering, and `LIMIT pageSize + 1` stay inside PostgreSQL.
7. Use FTS for words and trigram matching for bounded typo tolerance. Escape or
   parameterize all input; never construct SQL fragments from request values.
8. Cap query length, page size, source types, and result count. Reject blank or
   oversized queries.
9. Return type, source ID, title, bounded snippet, image reference, relevance,
   and visibility metadata safe for the client. Never return raw `tsvector`.
10. Build Explore as a separate application query using the same source
    permission functions:
    - group-visible recent records;
    - active curated Place/Product cards exposed by the V6 projection;
    - stable sort and optional topics;
    - no public/follower content and no internet call.
11. Add query-plan tests with representative seed volume and verify GIN/trigram
    indexes are used for target queries.

## Implementation symbols and query contracts

| Layer | Exact symbol | Required contract |
| --- | --- | --- |
| API | `WantToTryController#create`, `list`, `delete` | Implements only the frozen save/list/delete surface and never accepts an owner ID from the request. |
| API | `SearchController#search` | Validates `q`, source-type allow-list, cursor, and page size before invoking one permission-scoped query. |
| API | `ExploreController#get` | Returns only live group-authorised records and active curated cards, with no external lookup. |
| Use case | `SaveWantToTry#handle`, `ListWantToTry#handle`, `DeleteWantToTry#handle` | Resolves exactly one supported source, re-authorizes it on every list, and soft-deletes owner rows only. |
| Query | `AuthorisedSearchQuery#search` | Calls `public.foodmind_search_documents_for_user(:actorId, :q, :sourceTypes, :pageSize, :afterRelevance, :afterSortAt, :afterSourceType, :afterSourceId)`; V6 performs permission filtering, FTS/trigram matching, `(relevance, sort_at, source_type, source_id) DESC` keyset paging, and the bounded limit. |
| Query | `AuthorisedExploreQuery#explore` | Calls `public.foodmind_explore_documents_for_user(:actorId, :sourceTypes, :pageSize, :afterSortAt, :afterSourceType, :afterSourceId)`; V6 applies the source allow-list and `(sort_at, source_type, source_id) DESC` keyset before its bounded limit. |
| Repository | `WantToTryRepository#insertOrResolveDuplicate`, `findOwnerPage`, `softDeleteOwned` | Relies on the V6 exactly-one-source check and active partial unique indexes; never exposes a cross-owner existence difference. |

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/wanttotry/
src/main/java/com/foodmind/foodmindbackend/search/api/SearchController.java
src/main/java/com/foodmind/foodmindbackend/search/api/ExploreController.java
src/main/java/com/foodmind/foodmindbackend/search/application/
src/main/java/com/foodmind/foodmindbackend/search/infrastructure/persistence/
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/wanttotry/
src/test/java/com/foodmind/foodmindbackend/search/
```

## Commit plan

1. `feat(record): save permission-checked Want to Try references`
2. `feat(search): search authorised platform content`
3. `feat(search): compose permission-safe Explore results`
4. `test(security): cover revoked saved and search references`
5. `docs(api): publish saved search and Explore contracts`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | `src/main/java/com/foodmind/foodmindbackend/wanttotry/` and focused exactly-one/ownership tests |
| 2 | search controller/use case/query adapter plus relevance/bounds tests under `src/main/java/com/foodmind/foodmindbackend/search/` |
| 3 | Explore controller/use case/projections plus source allow-list tests |
| 4 | revoked-membership/private-source/SQL-injection/pagination matrix under `src/test/java/com/foodmind/foodmindbackend/wanttotry/`, `src/test/java/com/foodmind/foodmindbackend/search/`, and `src/test/java/com/foodmind/foodmindbackend/security/` |
| 5 | `src/main/resources/openapi/openapi.yaml` and saved/search/Explore examples only |

Add the next V12+ forward migration only when measured query/mapping evidence
requires it; never modify V6.

## Tests

- each Want to Try source type and exactly-one-source constraint;
- duplicate save/concurrent save;
- revoked group source on later list;
- private/cross-group leakage negative matrix;
- text relevance, typo tolerance, blank/oversized query;
- tied-score pagination stability with no duplicate/skip, source filtering, and
  query-plan/index evidence for both FTS and trigram paths;
- Explore contains only group-authorised or curated content;
- no external network call.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*WantToTry*,*Search*,*Explore*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "08 - Saved, Search and Explore"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
representative `EXPLAIN (ANALYZE, BUFFERS)` evidence uses the intended
GIN/trigram indexes, and revoked/private sources never appear.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `08 - Saved, Search and Explore`:

1. Save visible FoodRecord, Meal, Product, and Place; store `wantToTryId`.
2. List saved content.
3. Attempt to save secondary user's private record; expect `404`.
4. Search seeded Place/Food Product terms and the user's authorised
   FoodRecord terms. Meal is a saveable reference but is not a V6 search
   document type.
5. Search a group record as member.
6. Remove membership and repeat; source must disappear/be unavailable.
7. Fetch Explore and assert each item source is `GROUP_RECORD` or `CURATED_*`.
8. Blank/oversized search expects `400`.
9. Delete an owned Want to Try row and verify it no longer appears.

## Pull Request document

**Title**

```text
feat(search): add Want to Try and permission-safe discovery
```

**Body**

```markdown
## Summary

Adds saved authorised references, PostgreSQL platform search, and Explore
composition without introducing public visibility or external search.

## Implements

- BE-016, BE-017
- UC-03 saved/discovery portion
- UC-07 search foundation

## Database

- `V6__saved_search_and_explore.sql`
- FTS/trigram indexes: [list]
- representative query-plan evidence: [link]

## Permissions

[Describe each search branch and revoked-source behaviour.]

## Verification

- saved/search/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `08 - Saved, Search and Explore`: [PASS]

## Non-scope

- no public/follower feed
- no public internet or map search

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

- [ ] Permission predicates precede ranking
- [ ] Exactly one saved source is enforced in DB
- [ ] Revoked references do not resolve
- [ ] Query input is bounded/parameterized
- [ ] Explore source types are allow-listed
```
