# FoodMind Backend Postman Package

This package is directly importable into Postman and covers the complete frozen
FoodMind public Backend contract. It intentionally excludes private
`/internal/v1` Agent tools and every service credential.

## Files

- `FoodMind-Backend.postman_collection.json` — Collection v2.1 with 17
  folders and 191 executable requests.
- `FoodMind-Local.postman_environment.json` — local URLs and non-secret
  deterministic catalogue identifiers.
- `FoodMind-Staging.postman_environment.json` — placeholder
  `.example.test` staging URLs; replace them with the approved non-production
  deployment.
- `fixtures/foodmind-test.jpg` — 804-byte synthetic JPEG
  upload fixture, SHA-256 `8fd88d47b0705664840f59d6635d1439b845a4461d122b9d7d014f9e351a5a9a`.

## Import and run

1. Import the collection and exactly one environment.
2. Set `primaryPassword` and `secondaryPassword` locally. For staging, also
   replace `serviceUrl`, `baseUrl`, and the two test-account emails with
   approved non-production values. Never export populated secret current
   values back into Git.
3. Confirm `baseUrl` ends in `/api/v1`; `serviceUrl` must contain only the
   service origin/base context and is used for Actuator.
4. Run folders `00` through `14` in numeric order, run optional folder `15`
   only when media is part of the release, then run folder `16`. If media was
   explicitly deferred, record folder `15` as `N/A — media deferred` in the
   release evidence rather than treating its expected missing endpoints as
   failures. Authentication requests capture tokens; creation requests capture
   IDs; secondary-token requests exercise permission denial.
5. When rerunning against retained data, registration may return the documented
   duplicate conflict. Clear generated ID/idempotency variables for a wholly
   fresh run.

Postman Desktop resolves the bundled media file after the collection's file
body is associated with `postman/fixtures/foodmind-test.jpg`. Newman users
should invoke it from the repository root or pass that directory as the working
directory. The upload request has `noauth`, dynamically adds only the
allow-listed presigned headers, and checks that no Backend bearer token is sent.

## External Agent and analytics fixtures

Failure behavior is selected only through Backend/private dependency
configuration. The collection never sends a public test-control header, query
parameter, or body field.

1. Configure the Backend and private Agent stub/deployment externally.
2. Set the Postman-only `agentFixtureMode` gate to the matching value:
   `UNAVAILABLE`, `COLD_START`, `TIMEOUT`, `INVALID_OUTPUT`, or
   `COOKING_TIMEOUT`.
3. Run the matching request. Clear the variable after restoring normal Agent
   behavior.

Requests whose fixture gate is not selected are skipped by the Postman runner.
For the multiple-currency assertion, load the documented external data fixture
and set the local-only `analyticsFixtureMode=MULTI_CURRENCY`. Neither gate is
transmitted to FoodMind.

## Test behavior

Collection scripts generate one correlation ID per Backend request and stable
retry keys per environment. Error responses are checked for status, stable
uppercase code, safe message/path/trace fields, JSON media type, and absence of
stack traces, SQL/ORM details, credential hashes, private keys, raw prompts, or
Agent secrets.

Current coverage:

- 17 exact roadmap folders
- 191 total requests
- 63 requests with an expected error/security outcome
- 27 idempotency-key command/retry requests
- 47 secondary-identity or explicit permission checks
- 58 distinct planned public method/path pairs, including four catalogue reads
  and three media endpoints
- two Actuator requests plus a release readiness recheck
- UC-01 through UC-09 final smoke reads

## Safety

The committed environments contain no access token, refresh token, password,
invitation token, presigned URL, service token, real staging URL, or production
URL. `.example.test` values are deliberate non-routable placeholders.
