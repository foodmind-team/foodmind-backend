# ADR-003: Permission Query Model

## Status

Accepted

## Owner

Chen Yaqi

## Approval Date

2026-07-28

## Context

FoodMind stores sensitive user content, preferences, group membership, chat
references, and recommendation evidence. A caller must not be able to infer
another user's private resources through IDs, search results, group context, or
Agent delegation.

Permissions must remain consistent across Web, Android, public API, internal
tools, analytics, search, and recommendation workflows.

## Decision

The Backend derives the owner from the validated access JWT or short-lived
delegation token. Public requests never supply an authoritative actor ID.

Repository queries and SQL functions include owner and active-group-membership
predicates. Group-scoped access is reauthorised against current active
membership on every protected command and query. Existence-sensitive resources
return non-disclosing `404` when revealing the resource would leak another
user's data.

Internal Agent tools require both service identity and a short-lived delegation
JWT containing user, trace, audience, exact tool scopes, and any bounded group or
reference IDs. The Backend still performs live ownership and membership checks
inside the tool implementation.

Revocation blocks future resolution of affected records, group content, saved
references, and Chat sources. Previously generated user-owned conversation text
is retained for MVP history unless a stricter privacy requirement is approved.

## Alternatives Considered

- **Client-supplied owner IDs:** rejected because clients cannot be trusted to
  define authority.
- **Fetch broadly then filter in memory:** rejected because it risks data leaks,
  pagination defects, and accidental logging.
- **Static Agent service token plus user ID:** rejected because a compromised
  Agent could substitute identities.
- **Always return `403`:** rejected for existence-sensitive resources because it
  can disclose that a target exists.

## Consequences

- Every persistence query for user content must bind the authenticated owner or
  delegated user.
- Permission tests need both allow and deny cases, including removed group
  membership and revoked references.
- Search and Explore functions are permission-scoped only for the user ID
  supplied by Backend; they do not replace authentication.
- Analytics and ML export queries are Backend-owned and remain raw/sensitive
  until pseudonymised by an approved export pipeline.

## Security Implications

Authorization failures must not log sensitive payloads, raw prompts, comments,
tokens, or unrestricted query results. Internal services and clients never
receive direct PostgreSQL access. The raw ML export-source view is not a public
or client contract.

## Decision Gates

- Owner derivation: validated JWT/delegation token only.
- Resource non-disclosure: use `404` when existence itself is sensitive.
- Chat revocation: block new source resolution; retain existing user-owned text
  for MVP.
- Delegation: service identity plus scoped short-lived user-context token.
