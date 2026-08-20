# Chat Agent Contract Notes

Backend sends `chat-agent-v2` requests with a service token plus a short-lived
delegation token for internal tool calls. The Agent must echo `requestId`,
`sessionId`, `userMessageId`, and `traceId`.

Requests may include up to eight chronological `recentTurns`. These turns are
conversation context only and are never an authorised grounding source.

Supported routes are `SEARCH`, `SUMMARY`, `COMPARE`, `NAVIGATION`, and
`OUT_OF_SCOPE`. `OUT_OF_SCOPE` must use `UNSUPPORTED` and cite no sources.
Grounded answers may cite at most ten sources returned by authorised Backend
tools or shared references.

Responses may include up to three `suggestedQuestions` and three allowlisted
`suggestedDestinations`. Destinations are limited to `INVENTORY`,
`SHOPPING_LISTS`, `SAVED_RECIPES`, `COOKING_PLANS`, `RECOMMENDATIONS`, and
`EXPLORE`. Backend validates these fields before exposing them to clients.
