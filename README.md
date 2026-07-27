# FoodMind Backend

FoodMind Backend is the only public business API and the system of record for the FoodMind platform. Android and Web clients communicate with this service; they must never call the Agent or model-inference services directly.

> **Current status:** repository framework only. The package structure exists, but the business modules, API contracts, persistence model, security configuration, and service integrations described below are not yet implemented.

## Responsibilities

This repository owns:

- User registration, authentication, and JWT validation
- User preferences and hard dietary constraints
- Food and drink records
- Personal history
- Trusted groups, membership, and content visibility
- Group feeds and Want to Try items
- Meal, place, product, and recipe catalogue data
- Authorised platform search
- Recommendation-session orchestration
- Recommendation feedback events
- Cooking-plan and Chatbot request orchestration
- Dashboard metrics and weekly recaps
- PostgreSQL persistence and Flyway migrations
- Public OpenAPI contracts
- Permission checks, audit data, and trace correlation

This repository does not own:

- LangGraph state machines or Agent prompts
- UserCF, ItemCF, model training, or model evaluation
- Runtime model inference implementation
- Android or Web presentation logic
- Direct public-internet restaurant search

## System Boundary

```text
Android ─┐
         ├── HTTPS /api/v1 ──> Spring Boot ──> PostgreSQL
Web ─────┘                         │
                                  ├── private Agent service
                                  ├── private inference service
                                  └── optional S3 image storage
```

Spring Boot remains authoritative even when an internal service produces an AI or ML result. It authenticates the user, selects authorised context, validates the returned schema, persists the result, and decides what is safe to expose to the client.

## Repository Structure

```text
foodmind-backend/
├── .github/workflows/             # CI/CD workflows
├── docs/
│   ├── api/                       # API conventions and contract notes
│   ├── architecture/              # Backend architecture decisions
│   └── operations/                # Local and deployment runbooks
├── src/main/java/com/foodmind/foodmindbackend/
│   ├── common/
│   │   ├── api/                   # Shared API representations
│   │   ├── config/                # Application configuration
│   │   ├── error/                 # Error mapping
│   │   ├── security/              # Authentication and authorisation
│   │   └── validation/            # Shared validation rules
│   ├── auth/
│   ├── user/
│   ├── preference/
│   ├── catalog/
│   ├── record/
│   ├── group/
│   ├── wanttotry/
│   ├── search/
│   ├── recommendation/
│   ├── cooking/
│   ├── chat/
│   ├── analytics/
│   └── integration/
│       ├── agent/
│       ├── model/
│       └── storage/
├── src/main/resources/
│   ├── db/migration/              # Flyway migrations
│   ├── openapi/                   # Committed public API specification
│   └── seed/                      # Controlled catalogue seed data
└── src/test/
    ├── java/.../
    │   ├── unit/
    │   ├── integration/
    │   ├── contract/
    │   └── architecture/
    └── resources/
        ├── contracts/
        └── fixtures/
```

## Domain Modules

| Module | Intended ownership |
| --- | --- |
| `auth` | Registration, login, JWT issuance, authentication principal |
| `user` | Account identity and user-owned profile data |
| `preference` | Budget, cuisine, spice, dietary, location, and cleanliness preferences |
| `catalog` | Meal, Place, Food Product, and Recipe reference data |
| `record` | FoodRecord, DrinkRecord, history, ratings, and visibility |
| `group` | Trusted groups, invitations, memberships, roles, and feeds |
| `wanttotry` | Saved authorised references the user wants to try |
| `search` | Permission-aware PostgreSQL search |
| `recommendation` | Sessions, candidates, hard filters, orchestration, and feedback |
| `cooking` | Cooking-plan request validation, orchestration, and persistence |
| `chat` | Chat sessions, messages, and authorised source references |
| `analytics` | Shared metric definitions, dashboards, and weekly recap queries |

Package boundaries should be enforced through public application interfaces. A module must not reach into another module's persistence implementation.

## Public API

The canonical client-facing API is versioned under `/api/v1`.

Planned endpoint groups include:

- `/auth`
- `/users/me`
- `/users/me/preferences`
- `/food-records`
- `/drink-records`
- `/history`
- `/groups`
- `/want-to-try`
- `/recommendations`
- `/cooking-plans`
- `/chat`
- `/dashboard`
- `/weekly-recaps`

The committed OpenAPI document under `src/main/resources/openapi/` will be the source consumed by Android and Web. See [API conventions](docs/api/conventions.md).

## Internal Integrations

Internal endpoints and clients are not part of the public client contract.

- Backend to Agent: structured request containing authorised context and trace metadata
- Agent to inference service: structured feature/inference request
- Backend to storage: optional image-object operations
- Agent to Backend: narrow allow-listed tools for authorised search and content resolution

Every internal call must define:

- Authentication mechanism
- Request and response schema version
- Timeout and retry policy
- Correlation ID propagation
- Invalid-output handling
- Deterministic fallback behaviour

## Local Development

Prerequisites:

- Java 17
- PostgreSQL
- Docker Desktop, recommended for local dependencies
- Maven Wrapper prerequisites

Typical commands:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

```bash
./mvnw test
./mvnw spring-boot:run
```

The repository does not yet include a complete local profile or database configuration. Follow [local-development.md](docs/operations/local-development.md) when those files are introduced.

## Configuration Contract

Secret values must be supplied by the environment and must never be committed.

| Environment variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Select `local`, `test`, `staging`, or `production-demo` |
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | Least-privilege database user |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | JWT signing secret |
| `AGENT_SERVICE_BASE_URL` | Private Agent-service base URL |
| `AGENT_SERVICE_TOKEN` | Internal service credential |
| `INFERENCE_SERVICE_BASE_URL` | Private inference-service base URL |
| `INFERENCE_SERVICE_TOKEN` | Internal service credential |
| `S3_BUCKET` | Optional image bucket |
| `AWS_REGION` | AWS region for managed services |

The names above define the intended cross-environment contract. Application bindings must be documented when implemented.

## Testing Strategy

- Unit tests for domain rules and application services
- MockMvc tests for HTTP contracts and validation
- PostgreSQL integration tests with Testcontainers
- Permission tests for ownership and active group membership
- Contract tests for Agent and inference clients
- Timeout, invalid-response, and fallback tests
- Architecture tests for module boundaries
- Migration tests starting from an empty database

Tests must not require production credentials or unrestricted network access.

## Security Rules

- Authorisation is checked for every resource read and write.
- Group visibility requires active membership at request time.
- Search and Chatbot source resolution reuse the same permission policy.
- Agents receive authorised data or use narrow authorised tools; they do not access PostgreSQL.
- Logs must not contain passwords, JWTs, dietary-sensitive data, or full prompt content.
- Internal-service URLs and credentials remain server-side.
- Validation errors may identify fields but must not expose stack traces.

## Contribution Workflow

1. Create or reference an Issue with acceptance criteria.
2. Branch from the repository's protected default branch.
3. Keep changes inside the owning module.
4. Update OpenAPI before or with a public contract change.
5. Add tests for behaviour, permissions, and failures.
6. Run the full relevant test suite.
7. Open a Pull Request and obtain review.
8. Prefer squash merge after required checks pass.

Do not copy implementation code from another FoodMind repository. Coordinate through versioned contracts, examples, and test fixtures.

## Further Reading

- [Backend architecture](docs/architecture/overview.md)
- [API conventions](docs/api/conventions.md)
- [Local development](docs/operations/local-development.md)
