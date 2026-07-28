# Branch 03 — Registration, JWT Authentication, and Sessions

## Branch metadata

- **Branch:** `feat/auth-session`
- **Base dependency:** Branches 01–02 merged
- **Use case:** UC-01
- **BE items:** BE-006 and the authentication/current-user portion of BE-008
- **Schema foundation:** use `V2__identity_and_auth.sql` committed in Branch 01;
  do not edit it in this branch
- **Postman folder:** `02 - Authentication`

## Scope

Implement registration, login, access-JWT validation, rotating opaque refresh
sessions, logout, account-status enforcement, and distinct `401`/`403`
behaviour.

### Explicit non-scope

- No preference mutation, Admin API, password reset, or account-deletion flow.
- No long-lived bearer token in browser local storage.
- No social/group authorization beyond the common authenticated principal.
- No raw password or refresh token in persistence, logs, or audit metadata.

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/auth/api/AuthController.java
src/main/java/com/foodmind/foodmindbackend/auth/api/request/RegisterRequest.java
src/main/java/com/foodmind/foodmindbackend/auth/api/request/LoginRequest.java
src/main/java/com/foodmind/foodmindbackend/auth/api/response/AuthTokenResponse.java
src/main/java/com/foodmind/foodmindbackend/auth/application/RegisterUser.java
src/main/java/com/foodmind/foodmindbackend/auth/application/LoginUser.java
src/main/java/com/foodmind/foodmindbackend/auth/application/RefreshSession.java
src/main/java/com/foodmind/foodmindbackend/auth/application/LogoutSession.java
src/main/java/com/foodmind/foodmindbackend/auth/domain/RefreshToken.java
src/main/java/com/foodmind/foodmindbackend/auth/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/user/domain/User.java
src/main/java/com/foodmind/foodmindbackend/user/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/user/api/CurrentUserController.java
src/main/java/com/foodmind/foodmindbackend/user/application/GetCurrentUser.java
src/main/java/com/foodmind/foodmindbackend/common/security/SecurityConfiguration.java
src/main/java/com/foodmind/foodmindbackend/common/security/JwtIssuer.java
src/main/java/com/foodmind/foodmindbackend/common/security/FoodMindPrincipal.java
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/auth/
```

## Detailed implementation steps

1. Map `app_user` and `auth_session` exactly to V2. Keep API DTOs separate from
   JPA entities.
2. Normalize email using one documented algorithm before lookup/insert. Enforce
   uniqueness in both application handling and the database.
3. Validate registration:
   - syntactically valid email;
   - bounded display name;
   - password length and maximum byte/character limit;
   - no password returned or logged.
4. Hash passwords through Spring Security `PasswordEncoder`. Store only the
   encoded hash.
5. Implement registration in one transaction. Translate duplicate normalized
   email into a stable conflict without confirming account details beyond the
   approved UX.
6. Implement login:
   - retrieve by normalized email;
   - verify password;
   - reject suspended/deactivated accounts;
   - update `last_login_at`;
   - issue access JWT and opaque refresh token.
7. JWT claims: `sub`, `role`, `iss`, `aud`, `iat`, `nbf`, `exp`, `jti`.
   Validate signature, issuer, audience, time claims, and user status.
8. Generate refresh tokens with a cryptographically secure random source.
   Return the raw token once; store only SHA-256/HMAC hash.
9. Refresh rotation:
   - lock the current session row;
   - reject expired/revoked tokens;
   - create the replacement;
   - keep predecessor and successor under the same authenticated user and
     `token_family_id`; rely on V2's composite FK as the final guard;
   - require successor `issued_at` to be after the predecessor and reject
     cycles in the service-level transition policy;
   - set `rotated_at` and `replaced_by_session_id`;
   - detect reuse and revoke the token family;
   - rely on V2's mutation guard to reject token/identity rewrites,
     un-revocation, un-rotation/repointing, invalid successor chronology, and
     rotation cycles.
10. Logout revokes the current session; logout-all revokes all active sessions
    for the user.
11. Freeze transport:
    - Web: Secure HttpOnly refresh cookie plus Origin/CSRF protection;
    - Android: agreed secure token response/storage contract.
    If the team defers refresh transport, document re-login rather than
    committing a long-lived browser token.
12. Configure Security:
    - permit register/login/refresh and safe health endpoints;
    - authenticate `/api/v1/**`;
    - require service auth separately for `/internal/v1/**`;
    - use the shared error contract for authentication/denial.
13. Add the minimal authenticated `GET /users/me` read needed to prove the
    bearer chain end to end. Return only ID, email, display name, role, status,
    timezone, and version/timestamps already owned by V2. Branch 04 extends the
    same controller with profile mutation and preferences; it does not replace
    this route.
14. Add database cleanup for expired/revoked sessions as a bounded scheduled
    maintenance task; do not delete active sessions.

## Tests

- registration success, invalid fields, normalized-email duplicate;
- password hash is not raw and is verifiable;
- login success/wrong password/unknown email/suspended account;
- JWT issuer/audience/expiry/signature/status checks;
- refresh success, rotation, reuse detection, expiry, concurrent refresh,
  cross-user/family replacement rejection, successor chronology, and cycle
  rejection;
- direct database attempts to mutate the token hash/family, clear revocation,
  clear/repoint rotation, or delete an active session;
- logout and logout-all;
- no token/password in captured logs;
- unauthenticated `401` versus authenticated-but-denied `403`.

## Commit plan

1. `feat(auth): register users with secure password hashing`
2. `feat(auth): issue and validate access JWTs`
3. `feat(auth): rotate and revoke refresh sessions`
4. `test(auth): cover authentication and token abuse cases`
5. `docs(api): publish authentication OpenAPI contract`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | registration/user domain, application, persistence, controller DTOs, and focused registration tests under `src/main/java/com/foodmind/foodmindbackend/auth/`, `src/main/java/com/foodmind/foodmindbackend/user/`, and `src/test/java/com/foodmind/foodmindbackend/auth/` |
| 2 | JWT issuer/principal/security chain plus login code and matching tests under `src/main/java/com/foodmind/foodmindbackend/auth/` and `src/main/java/com/foodmind/foodmindbackend/common/security/` |
| 3 | refresh/logout/session lifecycle code and its concurrency/reuse tests under `src/main/java/com/foodmind/foodmindbackend/auth/` and `src/test/java/com/foodmind/foodmindbackend/auth/` |
| 4 | remaining abuse, log-redaction, and 401/403 matrix tests under `src/test/java/com/foodmind/foodmindbackend/auth/` and `src/test/java/com/foodmind/foodmindbackend/security/` |
| 5 | `src/main/resources/openapi/openapi.yaml` and authentication examples only |

If V2 cannot support the implemented mapping, add the next unused V12+
forward migration in the same commit as the first code that needs it; never
modify V2.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*Auth*,*Security*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "02 - Authentication"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
the negative session/rotation matrix passes. Query `auth_session` during local
testing and verify no raw refresh token is stored.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `02 - Authentication` executes:

1. Register primary user; accept `201` or an explicit duplicate-user setup
   path.
2. Login primary user; store `accessToken`.
3. Call protected `/users/me`; expect `200`.
4. Refresh; assert token changes and old refresh cannot be reused.
5. Login secondary user; store `secondaryAccessToken`.
6. Wrong password; expect `401`.
7. Missing access token; expect `401`.
8. Logout; verify refresh is rejected.
9. Logout all; verify every previously issued refresh session is rejected.
10. Login again for later folders.

Environment variables:
`primaryEmail`, `primaryPassword`, `secondaryEmail`, `secondaryPassword`,
`accessToken`, and `secondaryAccessToken`.

## Pull Request document

**Title**

```text
feat(auth): implement JWT authentication and rotating sessions
```

**Body**

```markdown
## Summary

Implements UC-01 authentication: registration, login, JWT validation, refresh
rotation, logout, and account-status enforcement.

## Implements

- BE-006
- UC-01 authentication portion

## Database

- `V2__identity_and_auth.sql`
- Tables: `app_user`, `auth_session`
- Empty migration: [PASS]
- Existing migration upgrade: [PASS]

## Security decisions

- password encoder: [name/config]
- access-token TTL: [duration]
- refresh transport: [Web/Android decision]
- refresh reuse behaviour: [describe]

## API

[List endpoints and OpenAPI commit.]

## Verification

- auth/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `02 - Authentication`: [PASS]
- log/token inspection: [PASS]

## Cross-repository action

- Web: implement agreed cookie/token flow
- Android: store session material in secure platform storage

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

- [ ] No raw password/refresh token stored or logged
- [ ] 401 and 403 are distinct
- [ ] issuer/audience/time claims validated
- [ ] refresh reuse test passes
- [ ] OpenAPI examples match runtime
```
