-- Older Cooking Agent releases embedded raw ingredient names in option IDs.
-- Keep enough room to materialize those already-completed async tasks while
-- newer agents enforce the canonical 128-character identifier contract.
ALTER TABLE public.cooking_plan_repair_option
    ALTER COLUMN option_id TYPE varchar(512);

ALTER TABLE public.cooking_plan_decision
    ALTER COLUMN option_id TYPE varchar(512);
