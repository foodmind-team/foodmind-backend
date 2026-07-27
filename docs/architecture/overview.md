# Backend Architecture

## Purpose

The backend is a modular Spring Boot application that exposes FoodMind's only public business API. It centralises security, persistence, business rules, permission-aware retrieval, orchestration, and analytics so Android and Web do not develop divergent behaviour.

This document describes the target architecture. The repository currently contains only the framework.

## Architectural Principles

1. **One public boundary:** only Spring Boot is reachable by user-facing clients.
2. **Package by business capability:** domain modules own their application and persistence concerns.
3. **Authorisation before retrieval:** no downstream service receives data that the user is not allowed to access.
4. **Deterministic rules before AI:** ordinary validation, filtering, and permission checks remain normal Java code.
5. **Structured internal contracts:** Agent and inference results are schema-validated.
6. **Persist evidence:** recommendations retain reason codes, model/fallback metadata, and trace IDs.
7. **Fallback is a product feature:** internal-service failure must not make core record functionality unavailable.

## Module Dependency Direction

```text
HTTP adapter
    ↓
application use case
    ↓
domain policy
    ↓
repository/integration interface
    ↓
JPA or HTTP adapter
```

Recommended rules:

- Controllers depend on application use cases, not JPA repositories.
- Domain policies do not depend on Spring MVC, HTTP clients, or database entities.
- Integration packages implement interfaces declared by the owning feature.
- Shared code belongs in `common` only when at least two modules require the same stable concept.
- A module may use another module through an explicit application-facing interface.

## Request Paths

### Standard CRUD request

```text
Client
  → authentication filter
  → controller and DTO validation
  → application use case
  → ownership/group-membership policy
  → repository
  → response mapping
```

### Recommendation request

```text
Client
  → Spring Boot authentication and validation
  → authorised candidate retrieval
  → hard-constraint filtering
  → recommendation-session persistence
  → private Agent service
  → private inference service, when available
  → structured result validation
  → candidate/evidence persistence
  → three recommendation cards
```

If Agent or inference processing fails, the backend returns a deterministic ranking with an explicit fallback status.

### Chatbot search request

```text
Client
  → Spring Boot
  → Chatbot Orchestrator
  → allow-listed authorised search tool
  → Spring Boot permission-aware search
  → grounded source references
  → validated response
```

A source reference never bypasses the original content permission.

## Persistence Boundaries

PostgreSQL is the system of record for:

- Identities and preferences
- Groups and memberships
- Food and drink records
- Catalogue records
- Recommendation sessions, candidates, and feedback
- Cooking-plan request/result metadata
- Chat sessions, messages, and source references
- Analytics source data

Important modelling rules:

- Persist `FoodRecord`; expose it as a Meal Note view for search and Chatbot use.
- `Private` records are visible only to their owner.
- `Group` records require a target group and active membership.
- Explicit recommendation acceptance is positive.
- Explicit rejection is negative.
- Passive non-selection remains unknown.
- Dashboard data is computed from source records rather than becoming a competing system of record.

## Transaction Boundaries

- Use one transaction for a single business state transition.
- Do not keep a database transaction open during a remote Agent or inference call.
- Persist a request/session state before the remote call.
- Update the state after validated success, timeout, or fallback.
- Use idempotency where a retried command could create duplicate sessions or feedback.

## Internal Service Resilience

Each internal client must define:

- Short connection and response timeouts
- Limited retries only for safe transient operations
- Circuit-breaker or failure-threshold policy when introduced
- Schema validation
- Trace propagation
- Explicit error translation
- Deterministic fallback

No internal failure should expose a raw upstream response to the client.

## Search

MVP search uses PostgreSQL full-text or trigram capabilities. Every query includes an authorisation scope derived from:

- User ownership
- Active group memberships
- Content visibility
- Content type

External search infrastructure is outside the MVP.

## Analytics

The backend owns metric definitions so both clients render identical values. Queries should return presentation-neutral DTOs containing labels, periods, values, units, and empty-state metadata.

## Evolution Rules

- Public breaking changes require a new API version or an agreed migration window.
- Database changes use forward-only Flyway migrations.
- Internal schema changes require matching contract tests in Backend and Intelligence.
- Model-version changes must not require a public client-contract change.
- New infrastructure is added only when the four-week MVP cannot meet a confirmed requirement without it.
