-- Saved references and permission-scoped PostgreSQL search/Explore support.

CREATE TABLE public.want_to_try (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    source_type varchar(20) NOT NULL,
    food_record_id uuid,
    meal_id uuid,
    food_product_id uuid,
    place_id uuid,
    note text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,

    CONSTRAINT fk_want_to_try_user
        FOREIGN KEY (user_id)
        REFERENCES public.app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_want_to_try_food_record
        FOREIGN KEY (food_record_id)
        REFERENCES public.food_record (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_want_to_try_meal
        FOREIGN KEY (meal_id)
        REFERENCES public.meal (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_want_to_try_food_product
        FOREIGN KEY (food_product_id)
        REFERENCES public.food_product (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_want_to_try_place
        FOREIGN KEY (place_id)
        REFERENCES public.place (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_want_to_try_source_type
        CHECK (source_type IN ('FOOD_RECORD', 'MEAL', 'FOOD_PRODUCT', 'PLACE')),
    CONSTRAINT ck_want_to_try_exactly_one_source
        CHECK (
            num_nonnulls(
                food_record_id,
                meal_id,
                food_product_id,
                place_id
            ) = 1
        ),
    CONSTRAINT ck_want_to_try_source_matches_type
        CHECK (
            (source_type = 'FOOD_RECORD' AND food_record_id IS NOT NULL)
            OR (source_type = 'MEAL' AND meal_id IS NOT NULL)
            OR (source_type = 'FOOD_PRODUCT' AND food_product_id IS NOT NULL)
            OR (source_type = 'PLACE' AND place_id IS NOT NULL)
        ),
    CONSTRAINT ck_want_to_try_note_length
        CHECK (note IS NULL OR char_length(note) <= 2000),
    CONSTRAINT ck_want_to_try_deleted_timestamp
        CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);

CREATE INDEX ix_want_to_try_user_created_active
    ON public.want_to_try (user_id, created_at DESC, id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_want_to_try_active_food_record
    ON public.want_to_try (user_id, food_record_id)
    WHERE source_type = 'FOOD_RECORD' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_want_to_try_active_meal
    ON public.want_to_try (user_id, meal_id)
    WHERE source_type = 'MEAL' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_want_to_try_active_food_product
    ON public.want_to_try (user_id, food_product_id)
    WHERE source_type = 'FOOD_PRODUCT' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_want_to_try_active_place
    ON public.want_to_try (user_id, place_id)
    WHERE source_type = 'PLACE' AND deleted_at IS NULL;

-- A food record is exposed to search as an authorised "Meal Note" projection.
-- Explicit 'simple' configuration keeps the generated expression immutable and
-- works predictably for multilingual names entered by users.
ALTER TABLE public.food_record
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(
            to_tsvector('simple'::regconfig, coalesce(meal_name_snapshot, '')),
            'A'
        )
        ||
        setweight(
            to_tsvector('simple'::regconfig, coalesce(place_name_snapshot, '')),
            'B'
        )
        ||
        setweight(
            to_tsvector('simple'::regconfig, coalesce(comment, '')),
            'C'
        )
    ) STORED;

CREATE INDEX ix_food_record_search_vector_active
    ON public.food_record
    USING gin (search_vector)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_food_record_meal_name_trgm_active
    ON public.food_record
    USING gin (lower(meal_name_snapshot) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_food_record_place_name_trgm_active
    ON public.food_record
    USING gin (lower(place_name_snapshot) gin_trgm_ops)
    WHERE place_name_snapshot IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_place_search_vector_active
    ON public.place
    USING gin (search_vector)
    WHERE curation_status = 'ACTIVE';

CREATE INDEX ix_place_name_trgm_active
    ON public.place
    USING gin (lower(name) gin_trgm_ops)
    WHERE curation_status = 'ACTIVE';

CREATE INDEX ix_place_area_trgm_active
    ON public.place
    USING gin (lower(area) gin_trgm_ops)
    WHERE area IS NOT NULL AND curation_status = 'ACTIVE';

CREATE INDEX ix_food_product_search_vector_active
    ON public.food_product
    USING gin (search_vector)
    WHERE curation_status = 'ACTIVE';

CREATE INDEX ix_food_product_name_trgm_active
    ON public.food_product
    USING gin (lower(name) gin_trgm_ops)
    WHERE curation_status = 'ACTIVE';

CREATE INDEX ix_food_product_brand_trgm_active
    ON public.food_product
    USING gin (lower(brand) gin_trgm_ops)
    WHERE brand IS NOT NULL AND curation_status = 'ACTIVE';

-- The application must pass the authenticated user's ID. Matching, permission
-- predicates, deterministic ordering, and bounded keyset pagination all remain
-- inside the function so authorised corpora are never materialised in Java.
CREATE FUNCTION public.foodmind_search_documents_for_user(
    p_user_id uuid,
    p_query text,
    p_source_types varchar(20)[] DEFAULT ARRAY[
        'FOOD_RECORD',
        'FOOD_PRODUCT',
        'PLACE'
    ]::varchar(20)[],
    p_page_size integer DEFAULT 20,
    p_after_relevance numeric(9,6) DEFAULT NULL,
    p_after_sort_at timestamptz DEFAULT NULL,
    p_after_source_type varchar(20) DEFAULT NULL,
    p_after_source_id uuid DEFAULT NULL
)
RETURNS TABLE (
    source_type varchar(20),
    source_id uuid,
    owner_user_id uuid,
    group_id uuid,
    visibility varchar(20),
    title text,
    subtitle text,
    body_excerpt text,
    occurred_at timestamptz,
    sort_at timestamptz,
    relevance numeric(9,6)
)
LANGUAGE plpgsql
STABLE
PARALLEL SAFE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF p_user_id IS NULL
       OR p_query IS NULL
       OR char_length(btrim(p_query)) NOT BETWEEN 1 AND 200
       OR p_source_types IS NULL
       OR cardinality(p_source_types) = 0
       OR p_page_size NOT BETWEEN 1 AND 100
       OR num_nonnulls(
           p_after_relevance,
           p_after_sort_at,
           p_after_source_type,
           p_after_source_id
       ) NOT IN (0, 4)
       OR EXISTS (
           SELECT 1
           FROM unnest(p_source_types) AS candidate(value)
           WHERE candidate.value IS NULL
              OR candidate.value <> ALL (
                  ARRAY[
                      'FOOD_RECORD',
                      'FOOD_PRODUCT',
                      'PLACE'
                  ]::varchar(20)[]
              )
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'invalid authorised-search arguments';
    END IF;

    RETURN QUERY
    WITH input AS MATERIALIZED (
        SELECT
            lower(btrim(p_query)) AS query_text,
            websearch_to_tsquery('simple'::regconfig, p_query) AS tsquery
    ),
    matching_document AS (
        SELECT
            'FOOD_RECORD'::varchar(20) AS result_source_type,
            record.id AS result_source_id,
            record.owner_user_id AS result_owner_user_id,
            record.group_id AS result_group_id,
            record.visibility AS result_visibility,
            record.meal_name_snapshot::text AS result_title,
            record.place_name_snapshot::text AS result_subtitle,
            left(record.comment, 500) AS result_body_excerpt,
            record.occurred_at AS result_occurred_at,
            record.occurred_at AS result_sort_at,
            greatest(
                ts_rank_cd(record.search_vector, input.tsquery, 32),
                public.similarity(
                    lower(record.meal_name_snapshot),
                    input.query_text
                ),
                coalesce(
                    public.similarity(
                        lower(record.place_name_snapshot),
                        input.query_text
                    ),
                    0
                )
            ) AS result_relevance
        FROM public.food_record AS record
        CROSS JOIN input
        WHERE 'FOOD_RECORD' = ANY (p_source_types)
          AND record.deleted_at IS NULL
          AND (
              record.owner_user_id = p_user_id
              OR (
                  record.visibility = 'GROUP'
                  AND EXISTS (
                      SELECT 1
                      FROM public.group_membership AS membership
                      WHERE membership.group_id = record.group_id
                        AND membership.user_id = p_user_id
                        AND membership.status = 'ACTIVE'
                  )
              )
          )
          AND (
              record.search_vector @@ input.tsquery
              OR lower(record.meal_name_snapshot)
                  OPERATOR(public.%) input.query_text
              OR lower(record.place_name_snapshot)
                  OPERATOR(public.%) input.query_text
          )

        UNION ALL

        SELECT
            'FOOD_PRODUCT'::varchar(20),
            product.id,
            NULL::uuid,
            NULL::uuid,
            'CURATED'::varchar(20),
            product.name::text,
            product.brand::text,
            left(product.description, 500),
            NULL::timestamptz,
            product.created_at,
            greatest(
                ts_rank_cd(product.search_vector, input.tsquery, 32),
                public.similarity(lower(product.name), input.query_text),
                coalesce(
                    public.similarity(lower(product.brand), input.query_text),
                    0
                )
            )
        FROM public.food_product AS product
        CROSS JOIN input
        WHERE 'FOOD_PRODUCT' = ANY (p_source_types)
          AND product.curation_status = 'ACTIVE'
          AND (
              product.search_vector @@ input.tsquery
              OR lower(product.name) OPERATOR(public.%) input.query_text
              OR lower(product.brand) OPERATOR(public.%) input.query_text
          )

        UNION ALL

        SELECT
            'PLACE'::varchar(20),
            place.id,
            NULL::uuid,
            NULL::uuid,
            'CURATED'::varchar(20),
            place.name::text,
            place.area::text,
            left(place.address_text, 500),
            NULL::timestamptz,
            place.created_at,
            greatest(
                ts_rank_cd(place.search_vector, input.tsquery, 32),
                public.similarity(lower(place.name), input.query_text),
                coalesce(
                    public.similarity(lower(place.area), input.query_text),
                    0
                )
            )
        FROM public.place AS place
        CROSS JOIN input
        WHERE 'PLACE' = ANY (p_source_types)
          AND place.curation_status = 'ACTIVE'
          AND (
              place.search_vector @@ input.tsquery
              OR lower(place.name) OPERATOR(public.%) input.query_text
              OR lower(place.area) OPERATOR(public.%) input.query_text
          )
    ),
    ranked_document AS (
        SELECT
            document.result_source_type,
            document.result_source_id,
            document.result_owner_user_id,
            document.result_group_id,
            document.result_visibility,
            document.result_title,
            document.result_subtitle,
            document.result_body_excerpt,
            document.result_occurred_at,
            document.result_sort_at,
            round(document.result_relevance::numeric, 6)::numeric(9,6)
                AS result_relevance
        FROM matching_document AS document
    )
    SELECT
        document.result_source_type,
        document.result_source_id,
        document.result_owner_user_id,
        document.result_group_id,
        document.result_visibility,
        document.result_title,
        document.result_subtitle,
        document.result_body_excerpt,
        document.result_occurred_at,
        document.result_sort_at,
        document.result_relevance
    FROM ranked_document AS document
    WHERE p_after_relevance IS NULL
       OR ROW(
           document.result_relevance,
           document.result_sort_at,
           document.result_source_type,
           document.result_source_id
       ) < ROW(
           p_after_relevance,
           p_after_sort_at,
           p_after_source_type,
           p_after_source_id
       )
    ORDER BY
        document.result_relevance DESC,
        document.result_sort_at DESC,
        document.result_source_type DESC,
        document.result_source_id DESC
    LIMIT p_page_size + 1;
END;
$function$;

-- Explore intentionally excludes private records. It contains only records in
-- the caller's currently active groups plus active curated catalogue content.
CREATE FUNCTION public.foodmind_explore_documents_for_user(
    p_user_id uuid,
    p_source_types varchar(20)[] DEFAULT ARRAY[
        'FOOD_RECORD',
        'FOOD_PRODUCT',
        'PLACE'
    ]::varchar(20)[],
    p_page_size integer DEFAULT 20,
    p_after_sort_at timestamptz DEFAULT NULL,
    p_after_source_type varchar(20) DEFAULT NULL,
    p_after_source_id uuid DEFAULT NULL
)
RETURNS TABLE (
    source_type varchar(20),
    source_id uuid,
    owner_user_id uuid,
    group_id uuid,
    visibility varchar(20),
    title text,
    subtitle text,
    body_excerpt text,
    occurred_at timestamptz,
    sort_at timestamptz
)
LANGUAGE plpgsql
STABLE
PARALLEL SAFE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF p_user_id IS NULL
       OR p_source_types IS NULL
       OR cardinality(p_source_types) = 0
       OR p_page_size NOT BETWEEN 1 AND 100
       OR num_nonnulls(
           p_after_sort_at,
           p_after_source_type,
           p_after_source_id
       ) NOT IN (0, 3)
       OR EXISTS (
           SELECT 1
           FROM unnest(p_source_types) AS candidate(value)
           WHERE candidate.value IS NULL
              OR candidate.value <> ALL (
                  ARRAY[
                      'FOOD_RECORD',
                      'FOOD_PRODUCT',
                      'PLACE'
                  ]::varchar(20)[]
              )
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'invalid authorised-Explore arguments';
    END IF;

    RETURN QUERY
    WITH document AS (
        SELECT
            'FOOD_RECORD'::varchar(20) AS result_source_type,
            record.id AS result_source_id,
            record.owner_user_id AS result_owner_user_id,
            record.group_id AS result_group_id,
            record.visibility AS result_visibility,
            record.meal_name_snapshot::text AS result_title,
            record.place_name_snapshot::text AS result_subtitle,
            left(record.comment, 500) AS result_body_excerpt,
            record.occurred_at AS result_occurred_at,
            record.occurred_at AS result_sort_at
        FROM public.food_record AS record
        WHERE 'FOOD_RECORD' = ANY (p_source_types)
          AND record.deleted_at IS NULL
          AND record.visibility = 'GROUP'
          AND EXISTS (
              SELECT 1
              FROM public.group_membership AS membership
              WHERE membership.group_id = record.group_id
                AND membership.user_id = p_user_id
                AND membership.status = 'ACTIVE'
          )

        UNION ALL

        SELECT
            'FOOD_PRODUCT'::varchar(20),
            product.id,
            NULL::uuid,
            NULL::uuid,
            'CURATED'::varchar(20),
            product.name::text,
            product.brand::text,
            left(product.description, 500),
            NULL::timestamptz,
            product.created_at
        FROM public.food_product AS product
        WHERE 'FOOD_PRODUCT' = ANY (p_source_types)
          AND product.curation_status = 'ACTIVE'

        UNION ALL

        SELECT
            'PLACE'::varchar(20),
            place.id,
            NULL::uuid,
            NULL::uuid,
            'CURATED'::varchar(20),
            place.name::text,
            place.area::text,
            left(place.address_text, 500),
            NULL::timestamptz,
            place.created_at
        FROM public.place AS place
        WHERE 'PLACE' = ANY (p_source_types)
          AND place.curation_status = 'ACTIVE'
    )
    SELECT
        item.result_source_type,
        item.result_source_id,
        item.result_owner_user_id,
        item.result_group_id,
        item.result_visibility,
        item.result_title,
        item.result_subtitle,
        item.result_body_excerpt,
        item.result_occurred_at,
        item.result_sort_at
    FROM document AS item
    WHERE p_after_sort_at IS NULL
       OR ROW(
           item.result_sort_at,
           item.result_source_type,
           item.result_source_id
       ) < ROW(
           p_after_sort_at,
           p_after_source_type,
           p_after_source_id
       )
    ORDER BY
        item.result_sort_at DESC,
        item.result_source_type DESC,
        item.result_source_id DESC
    LIMIT p_page_size + 1;
END;
$function$;

COMMENT ON FUNCTION public.foodmind_search_documents_for_user(
    uuid,
    text,
    varchar(20)[],
    integer,
    numeric,
    timestamptz,
    varchar,
    uuid
) IS
    'Bounded permission-scoped FTS/trigram search with deterministic keyset pagination.';
COMMENT ON FUNCTION public.foodmind_explore_documents_for_user(
    uuid,
    varchar(20)[],
    integer,
    timestamptz,
    varchar,
    uuid
) IS
    'Bounded permission-scoped Explore feed with deterministic keyset pagination; never private records.';
