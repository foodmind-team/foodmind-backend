# FoodMind Backend

FoodMind Backend is the public business API and system of record for FoodMind. It is the only service called by the Web and Android clients; private Agent and inference services are reached through this API, never directly from a client.

## Live deployment

The deployed FoodMind application is available at [https://13.229.2.154.sslip.io/](https://13.229.2.154.sslip.io/). The browser client uses the same HTTPS origin and reaches this Backend through `/api/v1`; private Agent and inference endpoints are not exposed publicly.

## What it provides

- Account authentication, profiles, preferences, and permission enforcement
- Food and drink records, history, trusted groups, Explore, search, and Want to Try
- Recommendation sessions and feedback, cooking-plan orchestration, chat, dashboards, and weekly recaps
- PostgreSQL persistence, Flyway migrations, a versioned OpenAPI contract, and optional private media storage

```text
Web / Android --> HTTPS /api/v1 --> Spring Boot --> PostgreSQL
                                        |--> private Agents
                                        |--> private inference service
                                        '--> optional S3-compatible storage
```

Spring Boot remains authoritative for authentication, authorisation, validation, persistence, and data returned to clients.

## Prerequisites

- Java 17
- Docker Desktop or Docker Engine with Compose
- Python 3 (for OpenAPI validation)

## Quick start

The following starts PostgreSQL locally and then runs the API on `http://localhost:8080`.

```bash
git clone https://github.com/foodmind-team/foodmind-backend.git
cd foodmind-backend
cp .env.example .env
docker compose up -d postgres
set -a; source .env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows, use `Copy-Item .env.example .env`, start PostgreSQL with `docker compose up -d postgres`, set the variables from `.env` in the current shell or IDE, then run `./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`.

The local profile applies Flyway migrations automatically. It uses the PostgreSQL port in `.env` (the example uses `5432`); change both `POSTGRES_PORT` and `DB_URL` if that port is occupied.

### Local endpoints

| Endpoint | URL |
| --- | --- |
| API base | `http://localhost:8080/api/v1` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Readiness probe | `http://localhost:8080/actuator/health/readiness` |

Private AI services are optional for basic CRUD development. Start the complete local dependency stack from [FoodMind Infrastructure](https://github.com/foodmind-team/foodmind-infra) when testing recommendation, cooking, or chat flows end to end.

## Configuration

Copy `.env.example` rather than committing credentials. The important local settings are:

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Local token configuration |
| `WEB_ALLOWED_ORIGINS`, `WEB_COOKIE_SECURE` | Browser CORS and refresh-cookie behaviour |
| `*_AGENT_BASE_URL`, `*_AGENT_SERVICE_TOKEN` | Server-to-server private Agent calls |
| `MEDIA_*` | Optional MinIO/S3 media support |
| `ONEMAP_*` | Optional OneMap walking-route integration |

The sample values are local-only placeholders. Never use them outside development, and never commit a populated `.env` file.

## API contract

The canonical public OpenAPI document is [`src/main/resources/openapi/openapi.yaml`](src/main/resources/openapi/openapi.yaml). Web and Android keep versioned snapshots of this contract. For a public API change, update the implementation, OpenAPI description, examples, and consumer snapshots together.

## Verify

```bash
python3 scripts/validate-openapi.py
python3 scripts/check-secrets.py
./mvnw -B --no-transfer-progress clean verify
```

Tests use disposable PostgreSQL containers where integration coverage requires a database. Docker must be available for those tests.

## Repository layout

```text
src/main/java/.../     Domain modules, HTTP API, security, and integrations
src/main/resources/    Configuration, Flyway migrations, OpenAPI, and seed data
src/test/              Unit, contract, architecture, and integration tests
docs/                  API, architecture, database, and operations documentation
postman/               Importable API collection and environment guidance
compose.yaml           Local PostgreSQL and MinIO dependencies
```

## Contributing

Keep a change within its owning module, use parameterised persistence APIs, and add behavioural and permission tests. Public contract changes must be backward-compatible unless an approved migration plan says otherwise. Before opening a pull request, run the applicable verification commands above and inspect `git diff --check`.

## Security

Do not add credentials, JWTs, personal data, or real prompts to source control or logs. Authorisation belongs on the server: client-side visibility is never a substitute for an ownership or active-group-membership check.

## License

No open-source license is currently included in this repository. Obtain permission from the maintainers before redistributing or reusing the code.
