# Recommendation Agent Contract Fixtures

Version: `recommendation-agent-v1`
Feature schema: `recommendation-features-v1`
Timeout budget: 2 seconds absolute deadline, with the Backend HTTP client using
bounded connect/read timeouts.

The Backend sends only authorised, already-filtered candidate IDs plus bounded
request, preference, and feature values. It never sends credentials, raw
records, comments, or unrelated group data. The Agent must echo the request,
session, and trace IDs and return at most three contiguous ranks.

Safe failure codes persisted by Backend fallback handling:

- `AGENT_DISABLED`
- `CONFIGURATION_ERROR`
- `TIMEOUT`
- `CONNECTION_ERROR`
- `NON_2XX`
- `MALFORMED_JSON`
- `OVERSIZED_RESPONSE`
- `SCHEMA_MISMATCH`
- `UNKNOWN_ID`
- `INVALID_REASON`
- `UNSUPPORTED_VERSION`
- `INFERENCE_UNAVAILABLE`
