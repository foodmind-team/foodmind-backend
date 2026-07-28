# Branch 07 — Trusted Groups, Membership, Visibility, and Feed

## Branch metadata

- **Branch:** `feat/groups-visibility`
- **Base dependency:** Branch 06 merged
- **Use case:** UC-03
- **BE items:** BE-013, BE-014, BE-015
- **Schema foundation:** `trusted_group`, `group_membership`, and
  `group_invitation` from V5 plus `group_recommendation_share` from V7,
  committed in Branch 01
- **Postman folder:** `06 - Groups and Visibility`

## Scope

Implement trusted-group creation, invitation/join, membership lifecycle,
role-based management, `PRIVATE`/`GROUP` record transitions, authorised group
feed, and sharing a selected recommendation into a group.

### Explicit non-scope

- No public/follower feed.
- No Group visibility for non-members.
- No polls/votes unless promoted by an approved scope decision.
- Group owners cannot edit another member's record.

## Detailed implementation steps

1. Define `GroupMembershipPolicy` as the single application-facing permission
   port for active membership and owner role checks.
2. Create group in one transaction with creator's active `OWNER` membership.
3. Create invitation:
   - owner only;
   - cryptographically random raw token returned once;
   - store only token hash;
   - enforce expiry, use count, and status.
4. Join:
   - lock invitation row;
   - reject expired/revoked/exhausted token;
   - activate or reactivate the user's membership according to the lifecycle
     decision;
   - increment use count atomically.
5. Member list requires active membership. Expose only profile fields approved
   for group display.
6. Remove member is owner-only; leaving is self-only. Prevent removal/leave of
   the last active owner until ownership transfer is complete.
7. Integrate FoodRecord create/update:
   - `GROUP` requires non-null group and active membership;
   - switching groups re-checks membership;
   - owner may edit their own group record even after leaving, but cannot select
     a new inaccessible group;
   - current active members retain read access while the record remains group
     visible.
8. Group feed query:
   - first authorize caller membership;
   - return non-deleted group-visible food/drink records and recommendation
     shares;
   - stable pagination by event time and ID;
   - do not include private records.
9. Recommendation share:
   - sharing user owns the recommendation session/candidate;
   - user is active in target group;
   - persist only candidate reference and bounded message;
   - re-authorize feed access at read time.
10. Archive a group rather than broad hard deletion. Define how archived group
    content appears to existing owners/members and test it.

## Implementation symbols and query contracts

| Layer | Exact symbol | Required contract |
| --- | --- | --- |
| API | `GroupController#create`, `list`, `get`, `update` | Implements `POST/GET /groups` and `GET/PATCH /groups/{groupId}`; obtains the actor only from the authenticated principal. |
| API | `GroupInvitationController#create`, `join` | Creates one-time-visible raw tokens and accepts only a raw join token; never returns or logs the stored hash. |
| API | `GroupMemberController#list`, `remove` | Implements member list/removal and maps the self-removal case to the same lifecycle policy rather than bypassing owner invariants. |
| API | `GroupFeedController#get` | Uses a bounded cursor and never assembles the feed by loading records and filtering them in Java. |
| API | `GroupRecommendationShareController#share` | Requires both candidate ownership and current active membership in the target group. |
| Policy | `GroupMembershipPolicy#requireActiveMember`, `requireOwner`, `assertLastOwnerRetained` | Is the shared decision point used by controllers/use cases and record visibility validation. |
| Query | `GroupFeedQueryAdapter#findVisibleEvents` | Authorizes with `group_membership(user_id = :actorId, group_id = :groupId, status = 'ACTIVE')` inside the query; selects only non-deleted `GROUP` records and authorised recommendation shares. |
| Ordering | `GroupFeedCursor#after` | Uses `(occurred_at DESC, source_type ASC, source_id DESC)` as the deterministic seek tuple; every cursor component is bound, not concatenated. |

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/group/api/
src/main/java/com/foodmind/foodmindbackend/group/application/
src/main/java/com/foodmind/foodmindbackend/group/domain/
src/main/java/com/foodmind/foodmindbackend/group/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/record/application/GroupVisibilityValidator.java
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/group/
src/test/java/com/foodmind/foodmindbackend/record/GroupRecordPermissionTest.java
```

## Commit plan

1. `feat(group): create trusted groups and secure invitations`
2. `feat(group): manage membership lifecycle and owner roles`
3. `feat(record): enforce group visibility on food records`
4. `feat(group): expose authorised group feed`
5. `feat(group): share recommendation candidates to groups`
6. `test(security): cover group membership visibility matrix`
7. `docs(api): publish group and feed contracts`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | group creation/invitation domain, ports, persistence, API, and focused tests under `src/main/java/com/foodmind/foodmindbackend/group/` |
| 2 | membership/role lifecycle code and owner/leave/concurrency tests under the group feature |
| 3 | `GroupVisibilityValidator`, food-record integration, and focused record permission tests |
| 4 | feed query/use case/DTO/controller and its pagination/security tests |
| 5 | recommendation-share use case/repository/API and focused ownership/membership tests |
| 6 | complete cross-state permission matrix under `src/test/java/com/foodmind/foodmindbackend/group/`, `src/test/java/com/foodmind/foodmindbackend/record/`, and `src/test/java/com/foodmind/foodmindbackend/security/` |
| 7 | `src/main/resources/openapi/openapi.yaml` and group/feed examples only |

V5 and V7 are immutable foundations. If implementation requires a correction,
add the next V12+ forward migration with the first affected code commit.

## Tests

- creator becomes owner;
- invitation hash/expiry/max-use/concurrent join;
- active, left, removed, and archived cases;
- last-owner invariant;
- private/group record allow/deny matrix;
- membership removed after a record/reference is created;
- another group's ID substitution;
- feed contains no private or cross-group content;
- recommendation share ownership and membership.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*Group*,*GroupRecordPermission*,*Security*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "06 - Groups and Visibility"
```

Expected: both Maven commands exit `0`; the Newman folder reports zero failed
assertions; the permission matrix proves that inactive, removed, archived, and
cross-group principals cannot obtain protected content.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `06 - Groups and Visibility`:

1. Primary creates group; store `groupId`.
2. List groups, then get and update the new group.
3. Primary creates invitation; store `groupInviteToken`.
4. Secondary joins and both users read the member list.
5. Primary creates group-visible food record.
6. Secondary reads record/feed successfully.
7. Unrelated user or invalid group ID cannot read.
8. Primary removes secondary.
9. Secondary feed/read now returns non-disclosing failure.
10. Primary attempts to leave as last owner; expect conflict.
11. Re-invite/rejoin secondary so later Search/Chat folders have an active
    membership.
12. The recommendation-share endpoint is executed in folder
    `09 - Recommendation Fallback` immediately after that folder creates the
    first owned candidate; Branch 07 still owns the endpoint and its automated
    controller/security tests.

## Pull Request document

**Title**

```text
feat(group): implement trusted groups and permission-safe visibility
```

**Body**

```markdown
## Summary

Implements UC-03 trusted groups, membership lifecycle, Group visibility, and
authorised feed behaviour.

## Implements

- BE-013, BE-014, BE-015
- UC-03 group/visibility/feed portion

## Permission matrix

[Paste allow/deny matrix and test class references.]

## Database

- Objects: trusted group, membership, invitation, recommendation share
- Migration/forward fix: [file or foundation]
- concurrency/constraint tests: [PASS]

## API

[List group, member, invitation, feed, and share endpoints.]

## Verification

- group/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `06 - Groups and Visibility`: [PASS]

## Non-scope

- no public/follower feed
- no group polls

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

- [ ] Invitation raw token is never stored
- [ ] Active membership is checked at query time
- [ ] Last owner cannot disappear accidentally
- [ ] Group owner cannot edit another user's record
- [ ] Private records never enter feed/search
```
