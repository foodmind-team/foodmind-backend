CREATE OR REPLACE FUNCTION public.foodmind_explore_documents_for_user(
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
                  ARRAY['FOOD_RECORD', 'FOOD_PRODUCT', 'PLACE']::varchar(20)[]
              )
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'invalid authorised-Explore arguments';
    END IF;

    RETURN QUERY
    WITH active_group AS MATERIALIZED (
        SELECT membership.group_id
        FROM public.group_membership AS membership
        JOIN public.trusted_group AS trusted_group
          ON trusted_group.id = membership.group_id
        WHERE membership.user_id = p_user_id
          AND membership.status = 'ACTIVE'
          AND trusted_group.status = 'ACTIVE'
    ),
    document AS (
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
          AND (
              record.owner_user_id = p_user_id
              OR (
                  record.visibility = 'GROUP'
                  AND EXISTS (
                      SELECT 1
                      FROM active_group
                      WHERE active_group.group_id = record.group_id
                  )
              )
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
