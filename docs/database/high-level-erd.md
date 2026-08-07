# FoodMind Backend High-Level ERD

Generated from the provided PostgreSQL schema dump. This is a simplified standard ERD that keeps only key business tables and main relationships.

```mermaid
erDiagram
    APP_USER {
        uuid id PK
        string email
        string role
        string status
    }

    USER_PREFERENCE {
        uuid user_id PK,FK
        decimal budget_min
        decimal budget_max
        string preferred_area
    }

    CATALOGUE {
        uuid id PK
        string type
        string name
        uuid cuisine_id FK
        uuid place_id FK
    }

    PLACE {
        uuid id PK
        string name
        string place_type
    }

    TRUSTED_GROUP {
        uuid id PK
        uuid created_by_user_id FK
        string name
    }

    GROUP_MEMBERSHIP {
        uuid id PK
        uuid group_id FK
        uuid user_id FK
        string role
        string status
    }

    FOOD_RECORD {
        uuid id PK
        uuid owner_user_id FK
        uuid meal_id FK
        uuid place_id FK
        uuid group_id FK
        string visibility
    }

    DRINK_RECORD {
        uuid id PK
        uuid owner_user_id FK
        uuid place_id FK
        uuid group_id FK
        string visibility
    }

    WANT_TO_TRY {
        uuid id PK
        uuid user_id FK
        string source_type
    }

    RECOMMENDATION_SESSION {
        uuid id PK
        uuid user_id FK
        uuid group_id FK
        string status
    }

    RECOMMENDATION_CANDIDATE {
        uuid id PK
        uuid session_id FK
        uuid catalogue_item_id FK
    }

    COOKING_PLAN {
        uuid id PK
        uuid user_id FK
        uuid source_recipe_id FK
        string status
    }

    CHAT_SESSION {
        uuid id PK
        uuid user_id FK
        string status
    }

    CHAT_MESSAGE {
        uuid id PK
        uuid session_id FK
        string role
    }

    CHAT_REFERENCE {
        uuid id PK
        uuid session_id FK
        string source_type
    }

    APP_USER ||--|| USER_PREFERENCE : has
    APP_USER ||--o{ TRUSTED_GROUP : creates
    APP_USER ||--o{ GROUP_MEMBERSHIP : joins
    TRUSTED_GROUP ||--o{ GROUP_MEMBERSHIP : has

    PLACE ||--o{ CATALOGUE : contains
    CATALOGUE ||--o{ FOOD_RECORD : referenced_by
    PLACE ||--o{ FOOD_RECORD : visited_at
    PLACE ||--o{ DRINK_RECORD : visited_at

    APP_USER ||--o{ FOOD_RECORD : creates
    APP_USER ||--o{ DRINK_RECORD : creates
    GROUP_MEMBERSHIP ||--o{ FOOD_RECORD : shares
    GROUP_MEMBERSHIP ||--o{ DRINK_RECORD : shares

    APP_USER ||--o{ WANT_TO_TRY : saves
    FOOD_RECORD ||--o{ WANT_TO_TRY : saved_from
    CATALOGUE ||--o{ WANT_TO_TRY : saved_from
    PLACE ||--o{ WANT_TO_TRY : saved_from

    APP_USER ||--o{ RECOMMENDATION_SESSION : requests
    TRUSTED_GROUP ||--o{ RECOMMENDATION_SESSION : scopes
    RECOMMENDATION_SESSION ||--o{ RECOMMENDATION_CANDIDATE : returns
    CATALOGUE ||--o{ RECOMMENDATION_CANDIDATE : recommended_as

    APP_USER ||--o{ COOKING_PLAN : generates
    CATALOGUE ||--o{ COOKING_PLAN : source_recipe

    APP_USER ||--o{ CHAT_SESSION : owns
    CHAT_SESSION ||--o{ CHAT_MESSAGE : contains
    CHAT_SESSION ||--o{ CHAT_REFERENCE : grounds
```

## Table Grouping

- `CATALOGUE` represents the key catalogue tables: `meal`, `food_product`, `recipe`, and related place offerings.
- Supporting tables are hidden to keep the ERD readable: auth sessions, media assets, invitations, taxonomy joins, recipe details, cooking details, chat citations, feedback details, audit/idempotency, and Flyway metadata.
