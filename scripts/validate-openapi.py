#!/usr/bin/env python3
"""Validate the committed public contract without downloading a generator."""

from pathlib import Path

try:
    import yaml
except ImportError as exc:  # pragma: no cover - environment diagnostic
    raise SystemExit("PyYAML is required to validate openapi.yaml") from exc


ROOT = Path(__file__).resolve().parents[1]
SPEC_PATH = ROOT / "src/main/resources/openapi/openapi.yaml"


def fail(message: str) -> None:
    raise SystemExit(f"OpenAPI validation failed: {message}")


document = yaml.safe_load(SPEC_PATH.read_text(encoding="utf-8"))
if document.get("openapi") not in {"3.0.3", "3.1.0"}:
    fail(f"unsupported OpenAPI version: {document.get('openapi')}")

paths = document.get("paths", {})
schemas = document.get("components", {}).get("schemas", {})


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


for node in walk(document):
    reference = node.get("$ref")
    if reference and reference.startswith("#/components/schemas/"):
        referenced_schema = reference.rsplit("/", 1)[-1]
        if referenced_schema not in schemas:
            fail(f"unresolved schema reference: {reference}")
required_paths = {
    "/recipes": {"get", "post"},
    "/recipes/{id}": {"get", "put", "delete"},
    "/cooking-plans/generate": {"post"},
}
for path, methods in required_paths.items():
    missing = methods - set(paths.get(path, {}))
    if missing:
        fail(f"{path} is missing operations: {', '.join(sorted(missing))}")

for path, methods in required_paths.items():
    for method in methods:
        if not paths[path][method].get("operationId"):
            fail(f"{method.upper()} {path} must declare operationId")

for schema in ("UserRecipeRequest", "UserRecipeResponse", "UserRecipePageResponse", "GenerateCookingPlanRequest", "CookingPlanResponse"):
    if schema not in schemas:
        fail(f"missing schema: {schema}")

recipe_request = schemas["UserRecipeRequest"]
for field in ("name", "servings", "ingredients", "steps"):
    if field not in recipe_request.get("required", []):
        fail(f"UserRecipeRequest must require {field}")

recipe_update = paths["/recipes/{id}"]["put"]
parameters = recipe_update.get("parameters", [])
if not any(parameter.get("name") == "If-Match" and parameter.get("required") for parameter in parameters):
    fail("recipe update must require If-Match")

generate = paths["/cooking-plans/generate"]["post"]
if not any(parameter.get("name") == "Idempotency-Key" and parameter.get("required") for parameter in generate.get("parameters", [])):
    fail("cooking plan generation must require Idempotency-Key")

print(f"OpenAPI contract OK: {SPEC_PATH}")
