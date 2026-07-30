# Cooking Agent Contract

Version: `cooking-agent-v1`

The backend sends only controlled active recipe candidates. The Agent must select
one supplied `recipeId` and return ordered ingredient, step, and warning
snapshots. It must not invent recipes, call recommendation/inference/chat
workflows, claim food safety, or return raw free-form text outside the structured
fields.

Failure responses are mapped by the backend to safe terminal plan states. Raw
Agent text is never returned to clients.
