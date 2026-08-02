# Backend Local Development

## Current State

The repository provides a PostgreSQL-backed local runtime baseline. Flyway owns
schema creation, and tests use a disposable PostgreSQL container rather than H2.

## Prerequisites

- Java 17
- Git
- Docker Desktop
- A shell capable of running the Maven Wrapper

Confirm Java:

```powershell
java -version
```

## Environment

Copy `.env.example` to `.env` for local Docker usage or configure the same
variables through the IDE. Do not commit populated secrets.

Minimum values:

```text
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:postgresql://localhost:5432/foodmind
DB_USERNAME=foodmind
DB_PASSWORD=<local-only-password>
JWT_ISSUER=foodmind-local
JWT_AUDIENCE=foodmind-clients
JWT_PUBLIC_KEY=<local-only-development-public-key-or-placeholder>
DELEGATION_JWT_ISSUER=foodmind-backend-local
DELEGATION_JWT_PUBLIC_KEY=<local-only-development-public-key-or-placeholder>
AGENT_SERVICE_BASE_URL=http://localhost:8001
AGENT_SERVICE_TOKEN=<local-only-token>
INFERENCE_SERVICE_BASE_URL=http://localhost:8002
INFERENCE_SERVICE_TOKEN=<local-only-token>
# Only for plain-http local Web development; keep the production default true.
WEB_COOKIE_SECURE=false
# Comma-separated browser origins allowed to call the API with credentials.
WEB_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174
```

Never commit the populated values.

When the Web client runs on `http://localhost`, set `WEB_COOKIE_SECURE=false`
in the local profile so the refresh cookie can be sent by the browser. The
production default remains secure-only. `WEB_ALLOWED_ORIGINS` must list the
exact Web dev origin(s); wildcard origins are not supported with credentials.

## Recommended Startup Order

1. PostgreSQL:

   ```powershell
   docker compose up -d postgres
   docker compose ps
   ```

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
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

## Database Workflow

- Create schema changes only through Flyway.
- Never enable automatic destructive schema recreation outside isolated tests.
- Migrations are ordered and forward-only.
- Seed catalogue data must be reproducible and safe to share.
- Testcontainers should create an isolated PostgreSQL instance for integration tests.
- Inspect migration history:

  ```powershell
  docker compose exec postgres psql -U foodmind -d foodmind -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
  ```

- Reset the local database volume:

  ```powershell
  docker compose down -v
  docker compose up -d postgres
  ```

- Stop PostgreSQL without deleting data:

  ```powershell
  docker compose down
  ```

## Expected Local URLs

These are the expected local endpoints after the backend profile is started:

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
