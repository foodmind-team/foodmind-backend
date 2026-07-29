# ADR-001: Modular Monolith Backend

## Status

Accepted

## Owner

Chen Yaqi

## Approval Date

2026-07-28

## Context

FoodMind has one backend codebase, one PostgreSQL system of record, a short MVP
delivery window, and workflows with strong transactional relationships across
identity, preferences, records, groups, recommendations, cooking, chat, and
analytics. Introducing independent backend services now would add service
discovery, distributed transactions, deployment sequencing, network failure
modes, and duplicate security logic before the domain boundaries are proven.

The architecture plan also requires clear module boundaries so future feature
work does not collapse into controller-to-repository shortcuts.

## Decision

FoodMind Backend will be delivered as a modular Spring Boot monolith.

Modules are package-level capability boundaries under
`com.foodmind.foodmindbackend`. HTTP controllers call application use cases;
application code calls domain policies and outbound ports; infrastructure
adapters implement those ports. Controllers must not inject persistence
repositories directly. Domain code must not depend on Spring MVC, JPA, Hibernate,
HTTP clients, or integration adapters. A module must not import another module's
`infrastructure` package.

Cross-module collaboration is synchronous for the MVP and goes through small
application-facing interfaces or database queries with explicit permission
predicates. The Backend remains the only public business API and the system of
record.

## Alternatives Considered

- **Microservices:** rejected because operational cost, distributed consistency,
  and deployment risk exceed the MVP benefit.
- **Layer-only monolith:** rejected because it permits accidental cross-feature
  persistence coupling.
- **Reactive stack:** rejected because the current workload is request/response
  JDBC/JPA and does not require a second runtime model.

## Consequences

- Feature branches remain small and reviewable while sharing one deployable
  artifact.
- Transactional boundaries stay local to PostgreSQL-backed use cases.
- Future service extraction remains possible only after a module has stable
  contracts, independent scaling pressure, and measured operational need.
- Architecture tests enforce the dependency rules from the first branch.

## Security Implications

The Backend is the public trust boundary. Permission checks are implemented in
application use cases and repository queries, not in clients or downstream
services. Internal Agent and inference calls cannot bypass Backend ownership and
group-membership rules.

## Decision Gates

- Admin API/UI is deferred; only `role` and `status` schema support is retained.
- Reusable pantry, group polls/votes, and photo upload are deferred until their
  later branch gates.
