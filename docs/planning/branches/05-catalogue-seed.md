# Branch 05 — Controlled Catalogue and Deterministic Seed Data

## Branch metadata

- **Branch:** `feat/catalogue-seed`
- **Base dependency:** Branch 04 merged
- **BE item:** BE-010
- **Schema foundation:** use `V4__catalogue.sql` and deterministic catalogue
  rows from `V11__seed_catalogue.sql`, both committed in Branch 01
- **Postman folder:** `04 - Catalogue`

## Scope

Implement read-only application access to controlled cuisines, meals, places,
place offerings, products, recipes, ingredients, dietary/allergen
classifications, and cleanliness-related observations. Supply deterministic,
shareable seed data supporting every later workflow.

### Explicit non-scope

- No public catalogue write/curation endpoint.
- No public-internet, map, ordering, or delivery-provider lookup.
- No inspection or food-safety certification claim.
- No edit to the immutable V4/V11 foundation in this feature branch.

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/catalog/api/CatalogueController.java
src/main/java/com/foodmind/foodmindbackend/catalog/application/
src/main/java/com/foodmind/foodmindbackend/catalog/domain/
src/main/java/com/foodmind/foodmindbackend/catalog/infrastructure/persistence/
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/catalog/
src/test/resources/fixtures/catalogue/
```

## Detailed implementation steps

1. Map V4 catalogue tables with UUID IDs, stable codes, active/retired status,
   money, location coordinates, classifications, and recipe ordering.
2. Model `place_meal` as the recommendation candidate; do not recommend an
   abstract Meal without its Place/price context for Eat out & delivery.
3. Expose application read ports:
   - reference-data lookup by stable code;
   - active offering candidate query;
   - place/meal/product detail projection;
   - controlled recipe candidates.
4. Keep curation writes outside public MVP endpoints. Seed/import maintenance
   is a controlled backend operation.
5. Implement read-only public catalogue endpoints only where clients need
   reference choices/details:
   - `GET /catalogue/reference-data`;
   - `GET /catalogue/meals/{id}`;
   - `GET /catalogue/places/{id}`;
   - `GET /catalogue/products/{id}`;
   - recipe detail may remain internal until Cooking.
6. Validate and consume the immutable V11 seed delivered in Branch 01. Verify
   its explicit UUIDs/timestamps, targeted `ON CONFLICT` keys, exact replay,
   synthetic provenance, and clean-database determinism; do not rebuild or
   edit V11 in this feature branch.
7. Confirm the supplied seed has enough variation:
   - several cuisines and meal types;
   - affordable and higher-budget offerings;
   - different spice levels and dietary/allergen combinations;
   - multiple areas/coordinates;
   - at least three viable recommendation types for fixture users;
   - products and places for Chatbot search;
   - recipes with ordered ingredients/steps for normal, no-match, and
     constraint fixtures.
8. Cleanliness observations must include source kind and timestamp and use
   decision-support wording. Do not seed inspection/certification claims.
9. Add repository projections to avoid N+1 queries.
10. Add uniqueness/check tests and assert every active offering references
    active catalogue parents.

## Commit plan

1. `feat(catalog): expose catalogue read ports and projections`
2. `test(catalog): validate catalogue constraints and fixtures`
3. `docs(api): publish catalogue reference endpoints`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | catalogue domain/application/read ports, persistence projections, API DTOs/controller, and focused tests under `src/main/java/com/foodmind/foodmindbackend/catalog/` |
| 2 | remaining catalogue mapping, constraint, deterministic-fixture, uniqueness, and N+1 tests under `src/test/java/com/foodmind/foodmindbackend/catalog/` and `src/test/resources/fixtures/catalogue/` |
| 3 | `src/main/resources/openapi/openapi.yaml` and catalogue examples only |

Treat V4 and V11 as immutable fixture/schema contracts. Add a new V12+
forward migration only for a reviewed correction; never change shared seed
identifiers in place.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*Catalog*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "04 - Catalogue"
```

Run migration twice only by recreating the test database; do not rerun/edit an
already applied versioned migration. Expected: both Maven commands exit `0`,
Newman reports zero failed assertions, and seed natural keys/row counts match
the V11 manifest without any public catalogue write path.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `04 - Catalogue`:

1. Get reference data and store one cuisine/allergen/dietary code.
2. Get a known seeded meal.
3. Get a known seeded place.
4. Get a known seeded product.
5. Unknown ID returns safe `404`.
6. Unauthenticated catalogue request follows the frozen security decision
   (normally `401` because platform content is authenticated).

## Pull Request document

**Title**

```text
feat(catalog): add controlled catalogue and deterministic seed data
```

**Body**

```markdown
## Summary

Adds the controlled catalogue used by records, recommendation, Cooking,
authorised search, and Chatbot grounding.

## Implements

- BE-010
- catalogue foundation for UC-02, UC-04, UC-06, UC-07, and UC-08

## Database

- `V4__catalogue.sql`
- catalogue portion of `V11__seed_catalogue.sql`
- empty migration and constraint tests: [PASS]
- seed provenance/licence: [describe]

## API / application ports

[List public reference endpoints and internal read ports.]

## Safety

- no public-internet search
- no inspection/food-safety guarantees
- no client catalogue write endpoint

## Verification

- catalogue tests: [PASS]
- full Maven tests: [PASS]
- Postman `04 - Catalogue`: [PASS]

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

- [ ] Seed UUIDs/natural keys are deterministic
- [ ] Offerings include price/currency and active status
- [ ] Dietary/allergen relations are tested
- [ ] Recipe steps/ingredients preserve order
- [ ] Search-ready fields contain no restricted data
```
