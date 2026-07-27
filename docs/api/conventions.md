# Public API Conventions

## Status

This document defines the intended public contract conventions. Endpoints are not implemented merely because they appear here.

## Base Path and Versioning

- Public client API: `/api/v1`
- Private service API: `/internal/v1`
- Android and Web may call only `/api/v1`.
- Internal endpoints must not be included in public client documentation.

## Media Type

Use JSON for request and response bodies unless a future photo-upload endpoint explicitly requires multipart data.

```http
Content-Type: application/json
Accept: application/json
```

## Authentication

Protected requests use:

```http
Authorization: Bearer <access-token>
```

Clients must not send internal service tokens. Authentication failure and authorisation failure are distinct:

- `401 Unauthorized`: missing, expired, or invalid authentication
- `403 Forbidden`: authenticated but not permitted
- `404 Not Found`: may be used when resource existence must not be disclosed

## Identifiers and Time

- Use opaque identifiers; clients must not derive meaning from them.
- Use ISO 8601 timestamps in UTC, for example `2026-07-28T10:15:30Z`.
- Date-only values use `YYYY-MM-DD`.
- Money uses a decimal value plus an explicit currency.
- Enum values use stable uppercase snake case.

## Trace Correlation

Clients may send:

```http
X-Correlation-ID: <client-generated-id>
```

The backend returns the accepted or generated correlation ID. Recommendation, cooking, and Chatbot responses also include a trace identifier in their structured body.

## Error Shape

All API errors should follow one stable shape:

```json
{
  "timestamp": "2026-07-28T10:15:30Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "The request contains invalid fields.",
  "path": "/api/v1/food-records",
  "traceId": "trace-123",
  "fieldErrors": [
    {
      "field": "price",
      "code": "POSITIVE_OR_ZERO",
      "message": "Price must be zero or greater."
    }
  ]
}
```

Rules:

- `code` is stable and machine-readable.
- `message` is safe for user display but is not the sole client decision signal.
- Stack traces, SQL, upstream payloads, and secrets are never returned.
- Field errors use public DTO field names.

## Validation

- Reject unknown or unsupported enum values.
- Apply size limits to comments, Chatbot messages, and free-text fields.
- Validate currency, ratings, dates, and visibility combinations.
- Validate hard constraints before starting Agent or inference work.
- Validate Agent output independently from Agent-side Pydantic validation.

## Pagination

List endpoints should use explicit page parameters:

```text
page=0
size=20
sort=createdAt,desc
```

Recommended response:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0,
  "hasNext": false
}
```

Enforce a maximum page size.

## Recommendation Contract

A recommendation-generation response should contain:

- `sessionId`
- `traceId`
- `modelVersion`, when inference was used
- `modelStatus`
- `fallbackStatus`
- Exactly three intentionally different recommendation cards when enough valid candidates exist
- Candidate, Meal, and Place identifiers as applicable
- Recommendation type: `PERSONAL`, `EXPLORATORY`, or `GROUP_INSPIRED`
- Structured reason codes
- Grounded explanation text

The model score is not itself an explanation.

## Feedback Contract

Feedback is a separate event, not an update hidden inside recommendation retrieval. Supported event concepts include:

- Acceptance
- Explicit rejection
- Rejection reason
- Re-recommendation request
- Later rating
- Would Eat Again

Passive non-selection must not be submitted as a negative label.

## Idempotency

Commands that may be retried should accept an idempotency key when duplicate creation would be harmful, especially:

- Recommendation generation
- Feedback submission
- Cooking-plan generation

The backend owns storage and expiry semantics for idempotency records.

## Compatibility

- Additive response fields are preferred.
- Do not rename or repurpose fields without a migration plan.
- Android and Web integrate against the committed OpenAPI version.
- Public-contract changes require example updates and client migration notes.
- Model and prompt changes should remain behind the existing public contract.
