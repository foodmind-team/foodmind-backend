-- Curated catalogue and evidence used by deterministic candidate retrieval.
-- Free-form internet ingestion is intentionally outside the MVP.

CREATE TABLE meal (
    id                    uuid         NOT NULL,
    name                  varchar(160) NOT NULL,
    description           text,
    cuisine_id            uuid         NOT NULL,
    meal_type             varchar(40)  NOT NULL,
    default_spice_level   smallint,
    curation_status       varchar(20)  NOT NULL DEFAULT 'DRAFT',
    created_at            timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_meal PRIMARY KEY (id),
    CONSTRAINT fk_meal_cuisine
        FOREIGN KEY (cuisine_id)
        REFERENCES cuisine (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_meal_name_not_blank
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_meal_description_not_blank
        CHECK (
            description IS NULL
            OR (description = btrim(description) AND description <> '')
        ),
    CONSTRAINT ck_meal_type
        CHECK (meal_type ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_meal_default_spice
        CHECK (
            default_spice_level IS NULL
            OR default_spice_level BETWEEN 0 AND 5
        ),
    CONSTRAINT ck_meal_curation_status
        CHECK (curation_status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_meal_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_meal_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE meal IS
    'Curated meal concept classified by cuisine and meal type.';
COMMENT ON COLUMN meal.default_spice_level IS
    'Curated default spice value on the inclusive 0-to-5 scale.';
COMMENT ON COLUMN meal.curation_status IS
    'Lifecycle state controlling whether the meal may enter live candidate retrieval.';

CREATE UNIQUE INDEX uq_meal_cuisine_name_type_ci
    ON meal (cuisine_id, lower(name), meal_type);

CREATE INDEX ix_meal_active_type_cuisine
    ON meal (meal_type, cuisine_id)
    WHERE curation_status = 'ACTIVE';

CREATE TRIGGER trg_meal_set_updated_at
BEFORE UPDATE ON meal
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE place (
    id                uuid         NOT NULL,
    name              varchar(160) NOT NULL,
    place_type        varchar(40)  NOT NULL,
    area              varchar(120) NOT NULL,
    address_text      text,
    latitude          numeric(9,6),
    longitude         numeric(9,6),
    price_band        smallint,
    curation_status   varchar(20)  NOT NULL DEFAULT 'DRAFT',
    search_vector     tsvector GENERATED ALWAYS AS (
        setweight(
            to_tsvector('simple'::regconfig, coalesce(name, '')),
            'A'
        )
        ||
        setweight(
            to_tsvector('simple'::regconfig, coalesce(area, '')),
            'B'
        )
        ||
        setweight(
            to_tsvector('simple'::regconfig, coalesce(address_text, '')),
            'C'
        )
        ||
        setweight(
            to_tsvector('simple'::regconfig, coalesce(place_type, '')),
            'D'
        )
    ) STORED,
    created_at        timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_place PRIMARY KEY (id),
    CONSTRAINT ck_place_name_not_blank
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_place_type
        CHECK (place_type ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_place_area_not_blank
        CHECK (area = btrim(area) AND area <> ''),
    CONSTRAINT ck_place_address_not_blank
        CHECK (
            address_text IS NULL
            OR (address_text = btrim(address_text) AND address_text <> '')
        ),
    CONSTRAINT ck_place_coordinate_pair
        CHECK (num_nonnulls(latitude, longitude) IN (0, 2)),
    CONSTRAINT ck_place_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_place_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_place_price_band
        CHECK (price_band IS NULL OR price_band BETWEEN 1 AND 4),
    CONSTRAINT ck_place_curation_status
        CHECK (curation_status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_place_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_place_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE place IS
    'Curated restaurant, cafe, hawker stall, or other food place.';
COMMENT ON COLUMN place.place_type IS
    'Application-supported uppercase place classification.';
COMMENT ON COLUMN place.price_band IS
    'Optional relative price band from 1 (lowest) through 4 (highest).';
COMMENT ON COLUMN place.latitude IS
    'Optional curated latitude for bounded distance calculations; no map integration is implied.';
COMMENT ON COLUMN place.longitude IS
    'Optional curated longitude for bounded distance calculations; no map integration is implied.';
COMMENT ON COLUMN place.search_vector IS
    'Stored weighted simple-dictionary vector; permission-scoped search indexes are added in V6.';

CREATE INDEX ix_place_name_area_ci
    ON place (lower(name), lower(area));

CREATE INDEX ix_place_active_area_price
    ON place (area, price_band)
    WHERE curation_status = 'ACTIVE';

CREATE TRIGGER trg_place_set_updated_at
BEFORE UPDATE ON place
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE place_meal (
    id                   uuid          NOT NULL,
    place_id             uuid          NOT NULL,
    meal_id              uuid          NOT NULL,
    display_name         varchar(160)  NOT NULL,
    price                numeric(10,2) NOT NULL,
    currency             char(3)       NOT NULL,
    spice_level          smallint,
    available            boolean       NOT NULL DEFAULT true,
    availability_note    text,
    created_at           timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              bigint        NOT NULL DEFAULT 0,

    CONSTRAINT pk_place_meal PRIMARY KEY (id),
    CONSTRAINT fk_place_meal_place
        FOREIGN KEY (place_id)
        REFERENCES place (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_place_meal_meal
        FOREIGN KEY (meal_id)
        REFERENCES meal (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_place_meal_seed_identity
        UNIQUE (place_id, meal_id, display_name),
    CONSTRAINT ck_place_meal_display_name_not_blank
        CHECK (display_name = btrim(display_name) AND display_name <> ''),
    CONSTRAINT ck_place_meal_price
        CHECK (price >= 0 AND price < 'Infinity'::numeric),
    CONSTRAINT ck_place_meal_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_place_meal_spice
        CHECK (spice_level IS NULL OR spice_level BETWEEN 0 AND 5),
    CONSTRAINT ck_place_meal_availability_note
        CHECK (
            availability_note IS NULL
            OR (
                availability_note = btrim(availability_note)
                AND availability_note <> ''
            )
        ),
    CONSTRAINT ck_place_meal_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_place_meal_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE place_meal IS
    'A specific meal offered at a place; this is the atomic recommendation candidate.';
COMMENT ON COLUMN place_meal.display_name IS
    'Place-specific menu name retained separately from the canonical meal name.';
COMMENT ON COLUMN place_meal.available IS
    'Curated current availability; false offerings must not enter candidate ranking.';
COMMENT ON COLUMN place_meal.availability_note IS
    'Optional safe curated context, never an unverified availability promise.';

CREATE INDEX ix_place_meal_available_candidate
    ON place_meal (meal_id, price, place_id)
    WHERE available;

CREATE INDEX ix_place_meal_meal
    ON place_meal (meal_id);

CREATE TRIGGER trg_place_meal_set_updated_at
BEFORE UPDATE ON place_meal
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE meal_dietary_tag (
    meal_id          uuid NOT NULL,
    dietary_tag_id   uuid NOT NULL,

    CONSTRAINT pk_meal_dietary_tag
        PRIMARY KEY (meal_id, dietary_tag_id),
    CONSTRAINT fk_meal_dietary_tag_meal
        FOREIGN KEY (meal_id)
        REFERENCES meal (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_meal_dietary_tag_tag
        FOREIGN KEY (dietary_tag_id)
        REFERENCES dietary_tag (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE meal_dietary_tag IS
    'Reviewed dietary classifications attached to a curated meal.';

CREATE INDEX ix_meal_dietary_tag_tag
    ON meal_dietary_tag (dietary_tag_id, meal_id);

CREATE TABLE meal_allergen (
    meal_id       uuid NOT NULL,
    allergen_id   uuid NOT NULL,

    CONSTRAINT pk_meal_allergen
        PRIMARY KEY (meal_id, allergen_id),
    CONSTRAINT fk_meal_allergen_meal
        FOREIGN KEY (meal_id)
        REFERENCES meal (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_meal_allergen_allergen
        FOREIGN KEY (allergen_id)
        REFERENCES allergen (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE meal_allergen IS
    'Reviewed known allergen classifications attached to a curated meal.';

CREATE INDEX ix_meal_allergen_allergen
    ON meal_allergen (allergen_id, meal_id);

CREATE TABLE place_observation (
    id                   uuid         NOT NULL,
    place_id             uuid         NOT NULL,
    observation_type     varchar(40)  NOT NULL,
    score                numeric(3,2) NOT NULL,
    note                 text,
    source_kind          varchar(40)  NOT NULL,
    observed_at          timestamptz  NOT NULL,
    created_by_user_id   uuid,
    created_at           timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_place_observation PRIMARY KEY (id),
    CONSTRAINT fk_place_observation_place
        FOREIGN KEY (place_id)
        REFERENCES place (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_place_observation_creator
        FOREIGN KEY (created_by_user_id)
        REFERENCES app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_place_observation_type
        CHECK (observation_type ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_place_observation_score
        CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT ck_place_observation_note
        CHECK (
            note IS NULL
            OR (note = btrim(note) AND note <> '' AND length(note) <= 2000)
        ),
    CONSTRAINT ck_place_observation_source_kind
        CHECK (source_kind ~ '^[A-Z][A-Z0-9_]{0,39}$')
);

COMMENT ON TABLE place_observation IS
    'Point-in-time decision-support evidence for a place; it is not an inspection or food-safety certification.';
COMMENT ON COLUMN place_observation.score IS
    'Normalised evidence score on the inclusive 0-to-1 scale.';
COMMENT ON COLUMN place_observation.source_kind IS
    'Uppercase provenance classification whose allowed business values are validated by the application.';

CREATE INDEX ix_place_observation_place_type_observed
    ON place_observation (place_id, observation_type, observed_at DESC);

CREATE INDEX ix_place_observation_creator
    ON place_observation (created_by_user_id)
    WHERE created_by_user_id IS NOT NULL;

CREATE TABLE food_product (
    id                uuid          NOT NULL,
    name              varchar(160)  NOT NULL,
    brand             varchar(120),
    description       text,
    price             numeric(10,2),
    currency          char(3),
    place_id          uuid,
    curation_status   varchar(20)   NOT NULL DEFAULT 'DRAFT',
    search_vector     tsvector GENERATED ALWAYS AS (
        setweight(
            to_tsvector('simple'::regconfig, coalesce(name, '')),
            'A'
        )
        ||
        setweight(
            to_tsvector('simple'::regconfig, coalesce(brand, '')),
            'B'
        )
        ||
        setweight(
            to_tsvector('simple'::regconfig, coalesce(description, '')),
            'C'
        )
    ) STORED,
    created_at        timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           bigint        NOT NULL DEFAULT 0,

    CONSTRAINT pk_food_product PRIMARY KEY (id),
    CONSTRAINT fk_food_product_place
        FOREIGN KEY (place_id)
        REFERENCES place (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_food_product_name_not_blank
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_food_product_brand_not_blank
        CHECK (
            brand IS NULL
            OR (brand = btrim(brand) AND brand <> '')
        ),
    CONSTRAINT ck_food_product_description_not_blank
        CHECK (
            description IS NULL
            OR (description = btrim(description) AND description <> '')
        ),
    CONSTRAINT ck_food_product_price
        CHECK (
            price IS NULL
            OR (price >= 0 AND price < 'Infinity'::numeric)
        ),
    CONSTRAINT ck_food_product_money_pair
        CHECK (
            (price IS NULL AND currency IS NULL)
            OR
            (price IS NOT NULL AND currency IS NOT NULL)
        ),
    CONSTRAINT ck_food_product_currency
        CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_food_product_curation_status
        CHECK (curation_status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_food_product_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_food_product_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE food_product IS
    'Curated packaged or standalone food item available to search and save.';
COMMENT ON COLUMN food_product.place_id IS
    'Optional curated place offering the product; null denotes a place-independent catalogue item.';
COMMENT ON COLUMN food_product.search_vector IS
    'Stored weighted simple-dictionary vector; permission-scoped search indexes are added in V6.';

CREATE INDEX ix_food_product_active_place
    ON food_product (place_id, name)
    WHERE curation_status = 'ACTIVE';

CREATE INDEX ix_food_product_place
    ON food_product (place_id);

CREATE TRIGGER trg_food_product_set_updated_at
BEFORE UPDATE ON food_product
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE food_product_dietary_tag (
    food_product_id   uuid NOT NULL,
    dietary_tag_id    uuid NOT NULL,

    CONSTRAINT pk_food_product_dietary_tag
        PRIMARY KEY (food_product_id, dietary_tag_id),
    CONSTRAINT fk_food_product_dietary_tag_product
        FOREIGN KEY (food_product_id)
        REFERENCES food_product (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_food_product_dietary_tag_tag
        FOREIGN KEY (dietary_tag_id)
        REFERENCES dietary_tag (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE food_product_dietary_tag IS
    'Reviewed dietary classifications attached to a curated food product.';

CREATE INDEX ix_food_product_dietary_tag_tag
    ON food_product_dietary_tag (dietary_tag_id, food_product_id);

CREATE TABLE food_product_allergen (
    food_product_id   uuid NOT NULL,
    allergen_id       uuid NOT NULL,

    CONSTRAINT pk_food_product_allergen
        PRIMARY KEY (food_product_id, allergen_id),
    CONSTRAINT fk_food_product_allergen_product
        FOREIGN KEY (food_product_id)
        REFERENCES food_product (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_food_product_allergen_allergen
        FOREIGN KEY (allergen_id)
        REFERENCES allergen (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE food_product_allergen IS
    'Reviewed known allergen classifications attached to a curated food product.';

CREATE INDEX ix_food_product_allergen_allergen
    ON food_product_allergen (allergen_id, food_product_id);

CREATE TABLE recipe (
    id                  uuid          NOT NULL,
    name                varchar(160)  NOT NULL,
    description         text,
    cuisine_id          uuid,
    default_servings    smallint      NOT NULL DEFAULT 1,
    prep_minutes        integer       NOT NULL DEFAULT 0,
    cook_minutes        integer       NOT NULL DEFAULT 0,
    estimated_cost      numeric(10,2),
    currency            char(3),
    curation_status     varchar(20)   NOT NULL DEFAULT 'DRAFT',
    created_at          timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT pk_recipe PRIMARY KEY (id),
    CONSTRAINT fk_recipe_cuisine
        FOREIGN KEY (cuisine_id)
        REFERENCES cuisine (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_recipe_name_not_blank
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_recipe_description_not_blank
        CHECK (
            description IS NULL
            OR (description = btrim(description) AND description <> '')
        ),
    CONSTRAINT ck_recipe_default_servings
        CHECK (default_servings > 0),
    CONSTRAINT ck_recipe_prep_minutes
        CHECK (prep_minutes >= 0),
    CONSTRAINT ck_recipe_cook_minutes
        CHECK (cook_minutes >= 0),
    CONSTRAINT ck_recipe_estimated_cost
        CHECK (
            estimated_cost IS NULL
            OR (
                estimated_cost >= 0
                AND estimated_cost < 'Infinity'::numeric
            )
        ),
    CONSTRAINT ck_recipe_money_pair
        CHECK (
            (estimated_cost IS NULL AND currency IS NULL)
            OR
            (estimated_cost IS NOT NULL AND currency IS NOT NULL)
        ),
    CONSTRAINT ck_recipe_currency
        CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_recipe_curation_status
        CHECK (curation_status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_recipe_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_recipe_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE recipe IS
    'Curated recipe metadata; cooking-plan outputs snapshot instructions separately.';
COMMENT ON COLUMN recipe.estimated_cost IS
    'Optional curated estimate in currency; it is not a live price quote.';

CREATE INDEX ix_recipe_active_cuisine
    ON recipe (cuisine_id, name)
    WHERE curation_status = 'ACTIVE';

CREATE INDEX ix_recipe_cuisine
    ON recipe (cuisine_id);

CREATE TRIGGER trg_recipe_set_updated_at
BEFORE UPDATE ON recipe
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE ingredient (
    id               uuid         NOT NULL,
    canonical_name   varchar(160) NOT NULL,
    default_unit     varchar(40),
    created_at       timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_ingredient PRIMARY KEY (id),
    CONSTRAINT ck_ingredient_name_not_blank
        CHECK (
            canonical_name = btrim(canonical_name)
            AND canonical_name <> ''
        ),
    CONSTRAINT ck_ingredient_default_unit
        CHECK (
            default_unit IS NULL
            OR (default_unit = btrim(default_unit) AND default_unit <> '')
        ),
    CONSTRAINT ck_ingredient_timestamp_order
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_ingredient_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE ingredient IS
    'Canonical ingredient vocabulary used by curated recipes.';
COMMENT ON COLUMN ingredient.default_unit IS
    'Optional human-readable default quantity unit; cooking outputs retain their own unit snapshots.';

CREATE UNIQUE INDEX uq_ingredient_canonical_name_ci
    ON ingredient (lower(canonical_name));

CREATE TRIGGER trg_ingredient_set_updated_at
BEFORE UPDATE ON ingredient
FOR EACH ROW
EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE recipe_ingredient (
    recipe_id      uuid          NOT NULL,
    ingredient_id  uuid          NOT NULL,
    quantity       numeric(12,3),
    unit           varchar(40),
    optional       boolean       NOT NULL DEFAULT false,
    sequence_no    smallint      NOT NULL,

    CONSTRAINT pk_recipe_ingredient
        PRIMARY KEY (recipe_id, ingredient_id),
    CONSTRAINT uq_recipe_ingredient_sequence
        UNIQUE (recipe_id, sequence_no),
    CONSTRAINT fk_recipe_ingredient_recipe
        FOREIGN KEY (recipe_id)
        REFERENCES recipe (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_recipe_ingredient_ingredient
        FOREIGN KEY (ingredient_id)
        REFERENCES ingredient (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_recipe_ingredient_quantity
        CHECK (
            quantity IS NULL
            OR (quantity > 0 AND quantity < 'Infinity'::numeric)
        ),
    CONSTRAINT ck_recipe_ingredient_unit
        CHECK (
            unit IS NULL
            OR (unit = btrim(unit) AND unit <> '')
        ),
    CONSTRAINT ck_recipe_ingredient_sequence
        CHECK (sequence_no > 0)
);

COMMENT ON TABLE recipe_ingredient IS
    'Ordered ingredient requirements for a curated recipe.';
COMMENT ON COLUMN recipe_ingredient.quantity IS
    'Optional positive quantity; null supports reviewed instructions such as seasoning to taste.';

CREATE INDEX ix_recipe_ingredient_ingredient
    ON recipe_ingredient (ingredient_id, recipe_id);

CREATE TABLE recipe_step (
    recipe_id     uuid      NOT NULL,
    step_no       smallint  NOT NULL,
    instruction   text      NOT NULL,

    CONSTRAINT pk_recipe_step PRIMARY KEY (recipe_id, step_no),
    CONSTRAINT fk_recipe_step_recipe
        FOREIGN KEY (recipe_id)
        REFERENCES recipe (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_recipe_step_number
        CHECK (step_no > 0),
    CONSTRAINT ck_recipe_step_instruction
        CHECK (
            instruction = btrim(instruction)
            AND instruction <> ''
            AND length(instruction) <= 4000
        )
);

COMMENT ON TABLE recipe_step IS
    'Ordered reviewed instructions for a curated recipe.';

CREATE TABLE recipe_dietary_tag (
    recipe_id        uuid NOT NULL,
    dietary_tag_id   uuid NOT NULL,

    CONSTRAINT pk_recipe_dietary_tag
        PRIMARY KEY (recipe_id, dietary_tag_id),
    CONSTRAINT fk_recipe_dietary_tag_recipe
        FOREIGN KEY (recipe_id)
        REFERENCES recipe (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_recipe_dietary_tag_tag
        FOREIGN KEY (dietary_tag_id)
        REFERENCES dietary_tag (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE recipe_dietary_tag IS
    'Reviewed dietary classifications attached to a curated recipe.';

CREATE INDEX ix_recipe_dietary_tag_tag
    ON recipe_dietary_tag (dietary_tag_id, recipe_id);

CREATE TABLE recipe_allergen (
    recipe_id     uuid NOT NULL,
    allergen_id   uuid NOT NULL,

    CONSTRAINT pk_recipe_allergen
        PRIMARY KEY (recipe_id, allergen_id),
    CONSTRAINT fk_recipe_allergen_recipe
        FOREIGN KEY (recipe_id)
        REFERENCES recipe (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_recipe_allergen_allergen
        FOREIGN KEY (allergen_id)
        REFERENCES allergen (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE recipe_allergen IS
    'Reviewed known allergen classifications attached to a curated recipe.';

CREATE INDEX ix_recipe_allergen_allergen
    ON recipe_allergen (allergen_id, recipe_id);
