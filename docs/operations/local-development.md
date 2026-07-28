# Backend Local Development

## Current State

The repository is scaffolded but does not yet provide a complete local runtime configuration. This runbook establishes the expected workflow for implementation.

## Prerequisites

- Java 17
- Git
- Docker Desktop or a local PostgreSQL installation
- A shell capable of running the Maven Wrapper

Confirm Java:

```powershell
java -version
```

## Environment

Create a local environment file outside version control or configure environment variables through the IDE.

Minimum planned values:

```text
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:postgresql://localhost:5432/foodmind
DB_USERNAME=foodmind
DB_PASSWORD=<local-only-password>
JWT_SECRET=<local-only-development-secret>
AGENT_SERVICE_BASE_URL=http://localhost:8001
AGENT_SERVICE_TOKEN=<local-only-token>
INFERENCE_SERVICE_BASE_URL=http://localhost:8002
INFERENCE_SERVICE_TOKEN=<local-only-token>
```

Never commit the populated values.

## Recommended Startup Order

1. PostgreSQL
2. Backend
3. Intelligence inference service
4. Intelligence Agent service
5. Web or Android client

The backend should remain usable for non-AI CRUD features if Intelligence is unavailable.

## Build and Test

Windows:

```powershell
.\mvnw.cmd clean test
```

macOS/Linux:

```bash
./mvnw clean test
```

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

## Database Workflow

- Create schema changes only through Flyway.
- Never enable automatic destructive schema recreation outside isolated tests.
- Migrations are ordered and forward-only.
- Seed catalogue data must be reproducible and safe to share.
- Testcontainers should create an isolated PostgreSQL instance for integration tests.

## Expected Local URLs

These are conventions to implement, not evidence of current availability:

| Service | URL |
| --- | --- |
| Backend API | `http://localhost:8080/api/v1` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Agent service | `http://localhost:8001` |
| Inference service | `http://localhost:8002` |

## Product-Flow Smoke Checks

Before an integration PR, verify:

1. A recommendation request with an authorised group returns an ordered
   candidate set with the lead candidate first.
2. The same response can drive Web and Android lead-result/alternate-result UI.
3. Cooking uses ingredient or pantry context without requiring automatic
   inventory collection.
4. Explore queries return only private-owner, active-group, or curated content
   permitted by the relevant endpoint.
5. Recommendation, Cooking Planner, and Chatbot remain separate request paths.

## Troubleshooting Checklist

### Application context fails

- Confirm the active profile.
- Confirm PostgreSQL is reachable.
- Confirm the local database exists.
- Confirm Flyway migrations are valid.
- Confirm required environment variables are present.

### Authentication tests fail

- Ensure tests use a test-only signing key.
- Check token clock-skew and expiry settings.
- Confirm unauthorised and forbidden cases are tested separately.

### Internal service calls fail

- Confirm the service URL is server-side, not a client URL.
- Check the internal token.
- Verify timeout configuration.
- Confirm the correlation ID appears in both services.
- Exercise the deterministic fallback path.

### Permission test fails

- Verify resource ownership.
- Verify group membership is active at request time.
- Verify the query itself is permission-scoped; filtering only after retrieval is insufficient.

## Before Opening a Pull Request

- Run unit, integration, and contract tests.
- Run migration validation from an empty database.
- Confirm no secret or personal test data is staged.
- Update OpenAPI for public changes.
- Update internal fixtures for Agent or inference changes.
- Document new environment-variable names without including values.
