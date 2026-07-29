# ADR-002: JWT Access Tokens and Rotating Refresh Sessions

## Status

Accepted

## Owner

Chen Yaqi

## Approval Date

2026-07-28

## Context

FoodMind must support Web and Android clients with a shared API security model.
Access tokens need to be short-lived and verifiable on each request, while
refresh credentials need revocation, reuse detection, and client-appropriate
transport. Long-lived bearer tokens in browser storage create unnecessary risk.

## Decision

FoodMind will issue short-lived signed access JWTs and rotating opaque refresh
tokens.

Access JWT validation includes issuer, audience, signature, expiry,
not-before/issued-at semantics, token ID, subject, role, and live account status.
Refresh tokens are high-entropy opaque values. Only their lower-case SHA-256
hashes are persisted in `auth_session`. Each rotation creates a successor in
the same user and token family, records the replaced session, and revokes the
family on reuse detection.

Web transport uses an HttpOnly, Secure refresh cookie with allowed-origin and
CSRF protections on refresh/logout. Android stores refresh tokens in platform
secure storage. Access tokens are not persisted in browser local storage.

JWT signing keys, refresh-token hashing behavior, service credentials, and
delegation-token signing keys are owned by Backend operations and rotated
through environment/secret-manager configuration, never source control.

## Alternatives Considered

- **Session-only cookies:** rejected because Android and internal clients still
  need bearer-style API access.
- **Non-rotating refresh tokens:** rejected because theft and replay detection
  are weak.
- **Long-lived access tokens:** rejected because account-status changes and
  credential compromise would remain effective too long.
- **Browser local storage for refresh tokens:** rejected because XSS impact is
  too high.

## Consequences

- Authentication implementation must rotate refresh sessions atomically.
- Reuse detection must revoke the token family and deny the request.
- Logout and account suspension must invalidate refresh sessions and make future
  access-token validation fail through account-status checks.
- Test fixtures must prove a refresh-session replacement cannot cross user or
  token-family boundaries.

## Security Implications

Tokens, token hashes, signing keys, service tokens, and full authentication
payloads must not be logged. Production and production-demo profiles require
secret values from the environment or secret manager. If refresh transport
cannot be completed safely, the fallback is short access tokens plus explicit
re-login, not long-lived browser storage.

## Decision Gates

- Web refresh transport: HttpOnly Secure cookie.
- Android refresh transport: secure platform storage.
- Secret ownership: Backend operations.
- Rotation behavior: one-way successor chain with family reuse revocation.
