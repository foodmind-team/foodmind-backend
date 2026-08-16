CREATE FUNCTION public.foodmind_media_object_key_for_user(
    p_user_id uuid,
    p_source_type varchar(20),
    p_source_id uuid
)
RETURNS text
LANGUAGE sql
STABLE
PARALLEL SAFE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
    SELECT asset.object_key
    FROM public.food_record AS record
    JOIN public.media_asset AS asset
      ON asset.id = record.media_asset_id
     AND asset.status = 'READY'
     AND asset.deleted_at IS NULL
    WHERE p_user_id IS NOT NULL
      AND p_source_type = 'FOOD_RECORD'
      AND record.id = p_source_id
      AND record.deleted_at IS NULL
      AND (
          record.owner_user_id = p_user_id
          OR (
              record.visibility = 'GROUP'
              AND EXISTS (
                  SELECT 1
                  FROM public.group_membership AS membership
                  JOIN public.trusted_group AS trusted_group
                    ON trusted_group.id = membership.group_id
                  WHERE membership.group_id = record.group_id
                    AND membership.user_id = p_user_id
                    AND membership.status = 'ACTIVE'
                    AND trusted_group.status = 'ACTIVE'
              )
          )
      )
    LIMIT 1
$function$;

COMMENT ON FUNCTION public.foodmind_media_object_key_for_user(uuid, varchar, uuid) IS
    'Returns a READY record image object key only when the caller remains authorised for the parent record.';
