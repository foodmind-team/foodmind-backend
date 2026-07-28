-- Preference taxonomies and per-user hard constraints.

CREATE TABLE cuisine (
    id          uuid         NOT NULL,
    code        varchar(40)  NOT NULL,
    name        varchar(100) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_cuisine PRIMARY KEY (id),
    CONSTRAINT uq_cuisine_code UNIQUE (code),
    CONSTRAINT ck_cuisine_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_cuisine_name_not_blank
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_cuisine_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_cuisine_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE cuisine IS
    'Controlled cuisine taxonomy shared by preferences, catalogue data, records, and recipes.';
COMMENT ON COLUMN cuisine.code IS
    'Stable uppercase machine code used in contracts and deterministic seed data.';

CREATE UNIQUE INDEX uq_cuisine_name_ci
    ON cuisine (lower(name));

CREATE TRIGGER trg_cuisine_set_updated_at
BEFORE UPDATE ON cuisine
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE dietary_tag (
    id          uuid         NOT NULL,
    code        varchar(40)  NOT NULL,
    name        varchar(100) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_dietary_tag PRIMARY KEY (id),
    CONSTRAINT uq_dietary_tag_code UNIQUE (code),
    CONSTRAINT ck_dietary_tag_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_dietary_tag_name_not_blank
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_dietary_tag_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_dietary_tag_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE dietary_tag IS
    'Controlled required dietary classifications such as VEGETARIAN.';
COMMENT ON COLUMN dietary_tag.code IS
    'Stable uppercase machine code; display text belongs in name.';

CREATE UNIQUE INDEX uq_dietary_tag_name_ci
    ON dietary_tag (lower(name));

CREATE TRIGGER trg_dietary_tag_set_updated_at
BEFORE UPDATE ON dietary_tag
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE allergen (
    id          uuid         NOT NULL,
    code        varchar(40)  NOT NULL,
    name        varchar(100) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_allergen PRIMARY KEY (id),
    CONSTRAINT uq_allergen_code UNIQUE (code),
    CONSTRAINT ck_allergen_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_allergen_name_not_blank
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_allergen_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_allergen_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE allergen IS
    'Controlled allergen taxonomy used for declared hard constraints and catalogue evidence.';
COMMENT ON COLUMN allergen.code IS
    'Stable uppercase machine code; display text belongs in name.';

CREATE UNIQUE INDEX uq_allergen_name_ci
    ON allergen (lower(name));

CREATE TRIGGER trg_allergen_set_updated_at
BEFORE UPDATE ON allergen
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE user_preference (
    user_id                                uuid          NOT NULL,
    budget_min                            numeric(10,2),
    budget_max                            numeric(10,2),
    currency                              char(3)       NOT NULL DEFAULT 'SGD',
    spice_tolerance                       smallint,
    preferred_area                        varchar(120),
    preferred_latitude                    numeric(9,6),
    preferred_longitude                   numeric(9,6),
    max_distance_km                       numeric(6,2),
    cleanliness_priority                  smallint      NOT NULL DEFAULT 0,
    minimum_cleanliness_evidence_score    numeric(3,2),
    food_goal                             varchar(40),
    drink_sweetness_preference            varchar(20),
    drink_ice_preference                  varchar(20),
    created_at                            timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                            timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                               bigint        NOT NULL DEFAULT 0,

    CONSTRAINT pk_user_preference PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preference_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_user_preference_budget_min
        CHECK (
            budget_min IS NULL
            OR (budget_min >= 0 AND budget_min < 'Infinity'::numeric)
        ),
    CONSTRAINT ck_user_preference_budget_max
        CHECK (
            budget_max IS NULL
            OR (budget_max >= 0 AND budget_max < 'Infinity'::numeric)
        ),
    CONSTRAINT ck_user_preference_budget_range
        CHECK (
            budget_min IS NULL
            OR budget_max IS NULL
            OR budget_min <= budget_max
        ),
    CONSTRAINT ck_user_preference_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_user_preference_spice
        CHECK (spice_tolerance IS NULL OR spice_tolerance BETWEEN 0 AND 5),
    CONSTRAINT ck_user_preference_area
        CHECK (
            preferred_area IS NULL
            OR (preferred_area = btrim(preferred_area) AND preferred_area <> '')
        ),
    CONSTRAINT ck_user_preference_coordinate_pair
        CHECK (num_nonnulls(preferred_latitude, preferred_longitude) IN (0, 2)),
    CONSTRAINT ck_user_preference_latitude
        CHECK (
            preferred_latitude IS NULL
            OR preferred_latitude BETWEEN -90 AND 90
        ),
    CONSTRAINT ck_user_preference_longitude
        CHECK (
            preferred_longitude IS NULL
            OR preferred_longitude BETWEEN -180 AND 180
        ),
    CONSTRAINT ck_user_preference_distance
        CHECK (
            max_distance_km IS NULL
            OR (
                max_distance_km > 0
                AND max_distance_km < 'Infinity'::numeric
                AND preferred_latitude IS NOT NULL
                AND preferred_longitude IS NOT NULL
            )
        ),
    CONSTRAINT ck_user_preference_cleanliness_priority
        CHECK (cleanliness_priority BETWEEN 0 AND 5),
    CONSTRAINT ck_user_preference_cleanliness_score
        CHECK (
            minimum_cleanliness_evidence_score IS NULL
            OR minimum_cleanliness_evidence_score BETWEEN 0 AND 1
        ),
    CONSTRAINT ck_user_preference_food_goal
        CHECK (
            food_goal IS NULL
            OR food_goal ~ '^[A-Z][A-Z0-9_]{0,39}$'
        ),
    CONSTRAINT ck_user_preference_drink_sweetness
        CHECK (
            drink_sweetness_preference IS NULL
            OR drink_sweetness_preference ~ '^[A-Z][A-Z0-9_]{0,19}$'
        ),
    CONSTRAINT ck_user_preference_drink_ice
        CHECK (
            drink_ice_preference IS NULL
            OR drink_ice_preference ~ '^[A-Z][A-Z0-9_]{0,19}$'
        ),
    CONSTRAINT ck_user_preference_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_user_preference_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE user_preference IS
    'One-to-one mutable user settings used by hard filtering and recommendation context.';
COMMENT ON COLUMN user_preference.currency IS
    'ISO 4217 currency for budget values; defaults to SGD for the MVP locale.';
COMMENT ON COLUMN user_preference.spice_tolerance IS
    'Maximum accepted spice level on the inclusive 0-to-5 scale.';
COMMENT ON COLUMN user_preference.preferred_latitude IS
    'Optional manually supplied latitude; this does not imply map integration.';
COMMENT ON COLUMN user_preference.preferred_longitude IS
    'Optional manually supplied longitude; this does not imply map integration.';
COMMENT ON COLUMN user_preference.minimum_cleanliness_evidence_score IS
    'Minimum decision-support evidence score on the inclusive 0-to-1 scale; not a safety certification.';
COMMENT ON COLUMN user_preference.version IS
    'Application-managed optimistic-lock version.';

CREATE INDEX ix_user_preference_area
    ON user_preference (preferred_area)
    WHERE preferred_area IS NOT NULL;

CREATE TRIGGER trg_user_preference_set_updated_at
BEFORE UPDATE ON user_preference
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE user_cuisine_preference (
    user_id       uuid        NOT NULL,
    cuisine_id    uuid        NOT NULL,
    preference    varchar(20) NOT NULL,

    CONSTRAINT pk_user_cuisine_preference
        PRIMARY KEY (user_id, cuisine_id),
    CONSTRAINT fk_user_cuisine_preference_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_user_cuisine_preference_cuisine
        FOREIGN KEY (cuisine_id)
        REFERENCES cuisine (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_user_cuisine_preference_value
        CHECK (preference IN ('LIKE', 'DISLIKE'))
);

COMMENT ON TABLE user_cuisine_preference IS
    'Explicit per-user LIKE or DISLIKE selection for a controlled cuisine.';

CREATE INDEX ix_user_cuisine_preference_cuisine
    ON user_cuisine_preference (cuisine_id);

CREATE TABLE user_dietary_tag (
    user_id         uuid NOT NULL,
    dietary_tag_id  uuid NOT NULL,

    CONSTRAINT pk_user_dietary_tag
        PRIMARY KEY (user_id, dietary_tag_id),
    CONSTRAINT fk_user_dietary_tag_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_user_dietary_tag_tag
        FOREIGN KEY (dietary_tag_id)
        REFERENCES dietary_tag (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE user_dietary_tag IS
    'Required dietary classifications for a user; absence is not an inferred preference.';

CREATE INDEX ix_user_dietary_tag_tag
    ON user_dietary_tag (dietary_tag_id);

CREATE TABLE user_allergen (
    user_id       uuid        NOT NULL,
    allergen_id   uuid        NOT NULL,
    severity      varchar(20) NOT NULL,

    CONSTRAINT pk_user_allergen
        PRIMARY KEY (user_id, allergen_id),
    CONSTRAINT fk_user_allergen_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_user_allergen_allergen
        FOREIGN KEY (allergen_id)
        REFERENCES allergen (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_user_allergen_severity
        CHECK (severity ~ '^[A-Z][A-Z0-9_]{0,19}$')
);

COMMENT ON TABLE user_allergen IS
    'User-declared allergen hard constraint with an application-supported uppercase severity code.';

CREATE INDEX ix_user_allergen_allergen
    ON user_allergen (allergen_id);

CREATE TABLE user_preferred_meal_type (
    user_id     uuid        NOT NULL,
    meal_type   varchar(40) NOT NULL,

    CONSTRAINT pk_user_preferred_meal_type
        PRIMARY KEY (user_id, meal_type),
    CONSTRAINT fk_user_preferred_meal_type_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_user_preferred_meal_type_code
        CHECK (meal_type ~ '^[A-Z][A-Z0-9_]{0,39}$')
);

COMMENT ON TABLE user_preferred_meal_type IS
    'Controlled uppercase meal-type codes explicitly preferred by the user.';
