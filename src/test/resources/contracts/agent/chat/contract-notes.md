# Chat Agent Contract Notes

Backend sends `chat-agent-v1` requests with a service token plus a short-lived
delegation token for internal tool calls. The Agent must echo `requestId`,
`sessionId`, `userMessageId`, and `traceId`.

Supported routes are `SEARCH`, `SUMMARY`, `COMPARE`, `NAVIGATION`, and
`OUT_OF_SCOPE`. `OUT_OF_SCOPE` must use `UNSUPPORTED` and cite no sources.
Grounded answers may cite at most ten sources returned by authorised Backend
tools or shared references.
