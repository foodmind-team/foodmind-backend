# Branch 04 — User Profile, Preferences, and Hard Constraints

## Branch metadata

- **Branch:** `feat/user-preferences`
- **Base dependency:** Branch 03 merged
- **Use case:** UC-01
- **BE items:** BE-007 and the preference/profile-mutation portion of BE-008
- **Schema foundation:** use `V3__preferences.sql` committed in Branch 01;
  do not edit it in this branch
- **Postman folder:** `03 - User and Preferences`

## Scope

Implement the current-user profile and a normalized preference model supporting
budget, cuisine likes/dislikes, spice tolerance, allergies, dietary
requirements, preferred meal types, area/distance, food goals, drink
preferences, timezone, and cleanliness decision-support priority.

### Explicit non-scope

- No Admin user management, password/session workflow, or account deletion.
- No medical, allergy-safety, or cleanliness certification guarantee.
- No comma-separated/JSON relationship storage.
- No automatic inference of pantry, inventory, or passive preferences.

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/user/api/CurrentUserController.java
src/main/java/com/foodmind/foodmindbackend/user/application/GetCurrentUser.java
src/main/java/com/foodmind/foodmindbackend/user/application/UpdateCurrentUser.java
src/main/java/com/foodmind/foodmindbackend/preference/api/UserPreferenceController.java
src/main/java/com/foodmind/foodmindbackend/preference/api/request/ReplacePreferencesRequest.java
src/main/java/com/foodmind/foodmindbackend/preference/api/response/UserPreferencesResponse.java
src/main/java/com/foodmind/foodmindbackend/preference/application/GetPreferences.java
src/main/java/com/foodmind/foodmindbackend/preference/application/ReplacePreferences.java
src/main/java/com/foodmind/foodmindbackend/preference/domain/
src/main/java/com/foodmind/foodmindbackend/preference/infrastructure/persistence/
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/user/
src/test/java/com/foodmind/foodmindbackend/preference/
```

## Detailed implementation steps

1. Map V3 reference and join tables. Do not store cuisine/allergy/diet lists as
   comma-separated strings or opaque JSON.
2. Read user ID only from `FoodMindPrincipal`; never accept an owner ID from
   the request.
3. Extend the Branch 03 `GET /users/me` projection without creating a second
   controller or incompatible DTO; preserve its auth contract and add only
   approved profile fields.
4. `PATCH /users/me` permits only display name and timezone changes. Email,
   password, role, and status require separate controlled workflows.
5. Validate IANA timezone identifiers.
6. `GET /users/me/preferences` assembles deterministic sorted lists using
   reference codes.
7. `PUT /users/me/preferences` replaces the complete preference aggregate in
   one transaction:
   - validate budget min/max and ISO currency;
   - validate spice/cleanliness ranges;
   - validate coordinates/distance if supplied; a saved maximum distance
     requires both preferred latitude and longitude;
   - reject the same cuisine in liked and disliked sets;
   - resolve all reference codes before deleting existing joins;
   - update scalar preference row and joins;
   - preserve the previous aggregate on any failure.
8. Distinguish hard constraints:
   allergies and required dietary tags are hard; likes, goals, and cleanliness
   priority are ranking/evidence inputs unless the request explicitly supplies
   a supported threshold.
9. Return safe field errors for unknown reference codes.
10. Create an application-facing `PreferenceQuery`/snapshot port for later
    recommendation and Cooking modules; do not expose preference repositories
    directly.

## Tests

- owner-only profile/preference access;
- invalid timezone, money, ranges, coordinate bounds;
- duplicate/contradictory cuisine selections;
- unknown dietary/allergen/cuisine codes;
- full replacement and transaction rollback;
- deterministic response ordering;
- concurrent version conflict if optimistic locking is exposed;
- sensitive values absent from logs.

## Commit plan

1. `feat(user): expose current-user profile`
2. `feat(preference): replace validated preference aggregate`
3. `test(preference): cover hard constraints and ownership`
4. `docs(api): publish user and preference contracts`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | current-profile controller/use cases/DTOs and focused tests under `src/main/java/com/foodmind/foodmindbackend/user/` and `src/test/java/com/foodmind/foodmindbackend/user/` |
| 2 | preference domain/application/persistence/API code and transactional replacement tests under `src/main/java/com/foodmind/foodmindbackend/preference/` |
| 3 | remaining preference validation, rollback, ordering, concurrency, security, and log tests under `src/test/java/com/foodmind/foodmindbackend/preference/` |
| 4 | `src/main/resources/openapi/openapi.yaml` and preference examples only |

Use a new V12+ forward migration only if implementation proves V3 requires a
change; never modify V3.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*User*,*Preference*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "03 - User and Preferences"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
cross-user IDs cannot alter results, incompatible preference mutations return
the frozen error shape, and no dietary/allergy value appears in logs.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `03 - User and Preferences`:

1. Get current profile.
2. Update display name/timezone.
3. Get initial preferences.
4. Replace preferences with budget, cuisine, allergen, dietary, meal-type,
   area, and cleanliness values.
5. Get and compare the saved aggregate.
6. Submit budget min greater than max; expect `400`.
7. Submit one cuisine as both like/dislike; expect `400`.
8. Call with secondary token and verify it returns only the secondary user's
   aggregate.

## Pull Request document

**Title**

```text
feat(preference): implement profile and hard-constraint preferences
```

**Body**

```markdown
## Summary

Completes the UC-01 profile/preference aggregate and creates the hard-constraint
input boundary used by recommendation and Cooking.

## Implements

- BE-007
- UC-01 profile/preferences

## Database

- `V3__preferences.sql`
- normalized cuisine, dietary, allergen, and meal-type joins
- migration tests: [PASS]

## Rules

[Describe budget, contradiction, timezone, and hard/soft constraint rules.]

## API

[List GET/PATCH/PUT endpoints and OpenAPI version.]

## Verification

- unit/integration/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `03 - User and Preferences`: [PASS]

## Cross-repository action

- Android/Web use reference codes and identical validation messages/codes.

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

- [ ] Owner derives from JWT
- [ ] Lists are normalized, not JSON/CSV
- [ ] Aggregate replacement is transactional
- [ ] Allergy/diet semantics are explicit
- [ ] No food-safety guarantee language
```
