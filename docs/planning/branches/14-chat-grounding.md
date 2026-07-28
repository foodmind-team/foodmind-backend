# Branch 14 — Authorised Chat Search, Summary, and Grounding

## Branch metadata

- **Branch:** `feat/chat-grounding`
- **Base dependency:** Branch 13 merged
- **Use cases:** UC-07, UC-08
- **BE items:** BE-027, BE-028
- **Schema foundation:** use `V9__chat.sql` committed in Branch 01; do not edit
  it in this branch
- **Postman folder:** `13 - Chat and Grounding`

## Scope

Implement user-owned Chat sessions/messages, shared content references,
short-lived delegated Agent tool access, authorised search/reference
resolution, grounded source persistence, unsupported-intent handling, and
revoked-source behaviour.

### Explicit non-scope

- No public-internet search or direct Agent/database access.
- No recommendation generation or Cooking orchestration from Chat.
- No arbitrary user ID in internal tool authorization.
- No raw prompt/message text, service token, or delegation token in logs.

## Detailed implementation steps

1. Freeze the public message/reference DTOs and private Chatbot contract.
2. Create/list/archive Chat sessions owner-only. Use status instead of broad
   hard deletion when messages/references exist.
3. Share reference:
   - exactly one FoodRecord/FoodProduct/Place;
   - verify session owner;
   - live-authorize source;
   - persist it with `origin=USER_SHARED` and a null
     `introducedByMessageId`; do not grant permanent access.
4. On user message:
   - validate bounded content;
   - persist user message and `PROCESSING` state in TX1;
   - mint a delegation JWT limited to user, audience, trace, tool scopes,
     optional IDs, and a short expiry;
   - close transaction before Agent call.
5. Internal tools require both Agent service identity and delegation:
   - authorised platform search;
   - resolve shared references;
   - return bounded DTOs only.
6. Internal tool code derives user/scope from signed delegation, not an
   arbitrary request `userId`, then reuses the search/reference permission
   policies from Branch 09.
7. Validate Agent result:
   - matching request/trace/route;
   - route only `SEARCH`, `SUMMARY`, `COMPARE`, `NAVIGATION`, or
     `OUT_OF_SCOPE`;
   - source IDs were returned by authorised tools/shared references;
   - claims/answer length and source list bounded;
   - no recommendation/Cooking/public-internet result.
8. Re-authorize every cited source before TX2. Persist assistant message and
   `chat_message_source` links. A reference created from a persisted message
   uses `origin=MESSAGE_INTRODUCED` and that same-session message ID.
9. Unsupported recommendation/Cooking intent returns a structured supported-
   scope explanation without invoking those workflows.
10. Revocation:
    - new resolution fails after group membership removal;
    - references show unavailable;
    - follow the accepted ADR for previously generated assistant text.
11. Logs exclude message content/prompts and delegation/service tokens.
12. Metrics record route, latency, tool failures, grounding rejection, and safe
    status—not content.

## Implementation symbols and query contracts

| Layer | Exact symbol | Required contract |
| --- | --- | --- |
| API | `ChatSessionController#create`, `list`, `get`, `archive` | Implements owner-scoped session creation/history/detail/archive; archive is a status transition, not destructive cascade. |
| API | `ChatMessageController#post`, `list` | Persists the bounded user message in TX1 and lists messages using a stable owner-scoped cursor. |
| API | `ChatReferenceController#share` | Resolves exactly one supported source and live-authorizes it before persisting a reference. |
| Transaction | `ChatTransactionService#beginMessage`, `completeGroundedMessage`, `markFailed` | TX1 commits before delegation/Agent calls; TX2 re-authorizes cited sources and atomically stores assistant message plus source links. |
| Security | `DelegationTokenIssuer#issue`; `InternalToolAuthorizer#requireScope` | Binds subject, audience, trace, scopes, optional resource IDs, and short expiry; requires both service identity and delegation. |
| Internal tool | `InternalSearchController#search`; `InternalReferenceController#resolve` | Derives actor/scope from verified claims, reuses Branch 09 permission queries, returns bounded DTOs, and stays outside public OpenAPI. |
| Validation | `ChatAgentResultValidator#validate` | Accepts only supported routes and source IDs returned by authorised tools/references; rejects ungrounded/oversized/foreign workflow output. |
| Query | `ChatQueryAdapter#findOwnedSession`, `findOwnedMessages`; `ChatReferenceQueryAdapter#resolveAuthorised` | Includes `user_id = :actorId` in session/message SQL; message cursor is `(created_at ASC, id ASC)` and every source is live-authorized at resolution/TX2. |

## Target files

```text
src/main/java/com/foodmind/foodmindbackend/chat/api/
src/main/java/com/foodmind/foodmindbackend/chat/application/
src/main/java/com/foodmind/foodmindbackend/chat/application/port/ChatAgentPort.java
src/main/java/com/foodmind/foodmindbackend/chat/domain/
src/main/java/com/foodmind/foodmindbackend/chat/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/integration/agent/ChatAgentHttpAdapter.java
src/main/java/com/foodmind/foodmindbackend/common/security/DelegationTokenIssuer.java
src/main/java/com/foodmind/foodmindbackend/common/security/InternalServiceSecurityConfiguration.java
src/main/java/com/foodmind/foodmindbackend/search/api/internal/
src/main/resources/openapi/openapi.yaml
src/test/resources/contracts/agent/chat/
src/test/java/com/foodmind/foodmindbackend/chat/
src/test/java/com/foodmind/foodmindbackend/search/
```

## Commit plan

1. `docs(api): freeze Chatbot and tool contract fixtures`
2. `feat(chat): manage owner-scoped conversations and references`
3. `feat(chat): secure delegated search and resolution tools`
4. `feat(chat): persist grounded Agent responses`
5. `test(security): cover Chatbot permission and grounding failures`
6. `docs(api): publish Chatbot public contracts`

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | versioned Chat/tool fixtures under `src/test/resources/contracts/agent/chat/` and contract notes |
| 2 | session/message/reference domain, persistence, public API/use cases, and owner tests |
| 3 | delegation issuer/validation, internal service security, internal search/reference tools, and security tests |
| 4 | Chat Agent adapter, orchestration/result validation, TX2 grounded-source persistence, and focused tests |
| 5 | remaining forged token, revoked source, unsupported intent, timeout, unsafe-output, and logging matrix |
| 6 | `src/main/resources/openapi/openapi.yaml` and public Chat examples only; internal tools remain outside public OpenAPI |

Use a new V12+ forward migration only when implementation proves V9 needs a
correction; never edit V9.

## Tests

- session owner/non-owner;
- exactly-one reference type;
- sharing into an empty session and origin/introducing-message nullability;
- `MESSAGE_INTRODUCED` cross-session message rejection;
- private/group/curated source allow/deny;
- membership revoked before/after sharing and before TX2;
- forged/expired/wrong-audience/over-scoped delegation;
- service identity missing/invalid;
- Agent cites unreturned or inaccessible source;
- unsupported recommendation/Cooking/public-search intents;
- timeout/malformed/ungrounded response;
- no content/token in logs.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*Chat*,*InternalTool*,*Delegation*"
.\mvnw.cmd test
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "13 - Chat and Grounding"
```

Expected: both Maven commands exit `0`; Newman reports zero failed assertions;
forged/expired/over-scoped delegation and revoked/foreign sources fail without
disclosure, the delayed Chatbot call holds no database transaction, and captured
logs contain no message/token material.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `13 - Chat and Grounding`:

1. Create session; store `chatSessionId`.
2. List sessions and get the created session.
3. Share authorised FoodRecord/Product/Place references.
4. Send search message; store `chatMessageId`; assert sources.
5. Send summary/compare message over shared references.
6. Page through message history and verify stable order.
7. Attempt to share private cross-user source; expect `404`.
8. Remove group membership and retry source resolution; expect unavailable.
9. Ask for recommendation/Cooking; assert unsupported routing.
10. Another authenticated user gets the session; expect `404`.
11. Archive the session and verify it is no longer active.

Private `/internal/v1` tools are exercised by backend contract tests, not the
public Postman collection.

## Pull Request document

**Title**

```text
feat(chat): implement authorised grounded Chatbot workflows
```

**Body**

```markdown
## Summary

Implements UC-07/UC-08 Chat search, summary, and comparison with live
permission checks, delegated tools, and persisted grounding references.

## Implements

- BE-027, BE-028
- UC-07, UC-08

## Database

- `V9__chat.sql`
- session/message/reference/source links
- migration tests: [PASS]

## Trust boundary

[Describe service identity, delegation claims/TTL/scopes, tool limits.]

## Grounding/revocation

[Describe source validation and accepted historical-text ADR.]

## Verification

- Chat/tool/security tests: [PASS]
- full Maven tests: [PASS]
- Postman `13 - Chat and Grounding`: [PASS]

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

- [ ] Agent cannot choose arbitrary user ID
- [ ] Every source is live-authorized
- [ ] Recommendation/Cooking stay separate
- [ ] No public internet tool exists
- [ ] Logs contain no chat content/tokens
```
