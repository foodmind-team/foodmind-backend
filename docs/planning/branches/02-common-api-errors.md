# Branch 02 — Common API Errors, Validation, and Correlation

## Branch metadata

- **Branch:** `feat/common-api-errors`
- **Base dependency:** Branch 01 merged
- **BE items:** BE-005 and common-contract portion of BE-008
- **Database:** no migration
- **Postman folder:** `01 - API Conventions`

## Scope

Create the single public error contract, correlation-ID lifecycle, validation
mapping, safe exception translation, pagination primitives, and OpenAPI common
components used by all feature branches.

### Explicit non-scope

- No business/domain endpoint.
- No new authentication/session design; Branch 03 consumes these handlers.
- No raw exception, SQL, or upstream body in the public contract.
- No RFC 9457 switch unless an approved ADR changes every client fixture.

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/common/api/ApiErrorResponse.java
src/main/java/com/foodmind/foodmindbackend/common/api/ApiFieldError.java
src/main/java/com/foodmind/foodmindbackend/common/api/PageResponse.java
src/main/java/com/foodmind/foodmindbackend/common/error/ApiException.java
src/main/java/com/foodmind/foodmindbackend/common/error/ErrorCode.java
src/main/java/com/foodmind/foodmindbackend/common/error/GlobalExceptionHandler.java
src/main/java/com/foodmind/foodmindbackend/common/observability/CorrelationIdFilter.java
src/main/java/com/foodmind/foodmindbackend/common/config/JacksonConfiguration.java
src/main/java/com/foodmind/foodmindbackend/common/config/OpenApiConfiguration.java
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/common/error/GlobalExceptionHandlerTest.java
src/test/java/com/foodmind/foodmindbackend/common/observability/CorrelationIdFilterTest.java
```

## Detailed implementation steps

1. Define immutable response records matching `docs/api/conventions.md`:
   `timestamp`, `status`, `code`, `message`, `path`, `traceId`, and
   `fieldErrors`.
2. Define stable error codes; begin with:
   `VALIDATION_ERROR`, `MALFORMED_JSON`, `AUTHENTICATION_REQUIRED`,
   `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, `CONFLICT`,
   `IDEMPOTENCY_CONFLICT`, `RATE_LIMITED`, `UPSTREAM_UNAVAILABLE`, and
   `INTERNAL_ERROR`.
3. Make `ApiException` carry only a safe error code/status/message. Do not pass
   arbitrary upstream or SQL text to the response.
4. In `GlobalExceptionHandler`, translate:
   - Bean Validation and method-validation failures;
   - unreadable JSON/unknown enum;
   - missing parameters and type mismatch;
   - domain not-found/conflict/forbidden exceptions;
   - authentication/authorisation failures using Security handlers;
   - unexpected exceptions to a generic safe message.
5. Use public DTO field names in field errors. Sort field errors
   deterministically for stable client tests.
6. Implement `CorrelationIdFilter`:
   - accept valid bounded `X-Correlation-ID`;
   - generate a UUID when absent/invalid;
   - put it in MDC for the request lifetime;
   - return it in the response;
   - clear MDC in `finally`;
   - never trust it as an authorisation value.
7. Define `PageResponse<T>` with `items`, zero-based `page`, bounded `size`,
   `totalItems`, `totalPages`, and `hasNext`.
8. Configure Jackson to fail on unknown request properties and serialize dates
   in ISO 8601 UTC.
9. Add OpenAPI reusable schemas, bearer scheme, correlation header,
   idempotency header, pagination parameters, and standard error responses.
10. Do not create a test-only production endpoint. Test the filter/handler with
    focused MockMvc test controllers or an invalid public path.

## Commit plan

1. `feat(api): add stable error and pagination contracts`
2. `feat(observability): propagate request correlation identifiers`
3. `test(api): cover validation and safe exception mapping`
4. `docs(api): add common OpenAPI components and examples`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | `src/main/java/com/foodmind/foodmindbackend/common/api/`, `src/main/java/com/foodmind/foodmindbackend/common/error/` except correlation-only code |
| 2 | `src/main/java/com/foodmind/foodmindbackend/common/observability/` and correlation-specific configuration |
| 3 | `src/test/java/com/foodmind/foodmindbackend/common/error/`, `src/test/java/com/foodmind/foodmindbackend/common/observability/` |
| 4 | `src/main/resources/openapi/openapi.yaml`, `docs/api/conventions.md` when changed |

When one production class needs tests, keep those tests in the same production
commit; commit 3 is for the cross-cutting matrix and regression fixtures.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*GlobalExceptionHandlerTest,*CorrelationIdFilterTest"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "01 - API Conventions"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
example responses contain no stack trace, exception class, SQL, or upstream
payload.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `01 - API Conventions`:

1. `GET /api/v1/does-not-exist` expects a safe `404` envelope.
2. Repeat with `X-Correlation-ID: postman-correlation-test`; assert the response
   header and body trace ID match the accepted ID.
3. Send malformed JSON to the first available JSON endpoint after Branch 03;
   expect `400/MALFORMED_JSON`.
4. Send an unknown field after Branch 03; expect `400/VALIDATION_ERROR` or the
   frozen equivalent.

## Pull Request document

**Title**

```text
feat(api): standardise errors, validation, and correlation
```

**Body**

```markdown
## Summary

Adds the shared API behaviour all FoodMind feature controllers must use:
stable errors, validation mapping, pagination, correlation IDs, and OpenAPI
common schemas.

## Implements

- BE-005
- common-contract portion of BE-008

## Contract changes

- New reusable error/page schemas
- `X-Correlation-ID` request/response behaviour
- stable machine-readable error codes

## Security

- raw exceptions, SQL, stack traces, and upstream bodies are suppressed
- correlation values are bounded and are not used for authorisation

## Verification

- focused MockMvc tests: [PASS]
- full Maven test: [PASS]
- Postman `01 - API Conventions`: [PASS]

## Database

No migration.

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

- [ ] Field errors use public DTO names
- [ ] Unknown request fields are rejected
- [ ] MDC is cleared after every request
- [ ] OpenAPI examples match runtime JSON
- [ ] Android/Web migration note supplied
```
