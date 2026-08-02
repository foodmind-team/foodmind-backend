CREATE TABLE user_recipe (
    id                  uuid         NOT NULL,
    owner_user_id       uuid         NOT NULL,
    name                varchar(160) NOT NULL,
    servings             integer      NOT NULL DEFAULT 2,
    image_url            varchar(2048),
    tags_json            jsonb        NOT NULL DEFAULT '[]'::jsonb,
    allergen_hints_json  jsonb        NOT NULL DEFAULT '[]'::jsonb,
    ingredients_json     jsonb        NOT NULL DEFAULT '[]'::jsonb,
    steps_json           jsonb        NOT NULL DEFAULT '[]'::jsonb,
    created_at           timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at           timestamptz,
    version              bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_user_recipe PRIMARY KEY (id),
    CONSTRAINT fk_user_recipe_owner FOREIGN KEY (owner_user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_recipe_name_not_blank CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_user_recipe_servings CHECK (servings BETWEEN 1 AND 50),
    CONSTRAINT ck_user_recipe_json_arrays CHECK (
        jsonb_typeof(tags_json) = 'array'
        AND jsonb_typeof(allergen_hints_json) = 'array'
        AND jsonb_typeof(ingredients_json) = 'array'
        AND jsonb_typeof(steps_json) = 'array'
    ),
    CONSTRAINT ck_user_recipe_version_nonnegative CHECK (version >= 0),
    CONSTRAINT ck_user_recipe_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_user_recipe_owner_updated
    ON user_recipe (owner_user_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_user_recipe_set_updated_at
BEFORE UPDATE ON user_recipe
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();
