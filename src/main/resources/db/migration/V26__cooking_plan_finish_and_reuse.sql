ALTER TABLE public.cooking_plan
    ADD COLUMN finished_at timestamptz,
    ADD COLUMN request_fingerprint varchar(64),
    ADD COLUMN reused_from_plan_id uuid;

ALTER TABLE public.cooking_plan
    ADD CONSTRAINT fk_cooking_plan_reused_from
        FOREIGN KEY (reused_from_plan_id) REFERENCES public.cooking_plan(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_cooking_plan_finished_at
        CHECK (finished_at IS NULL OR (status = 'READY' AND finished_at >= created_at)),
    ADD CONSTRAINT ck_cooking_plan_request_fingerprint
        CHECK (request_fingerprint IS NULL OR request_fingerprint ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_cooking_plan_reuse_identity
        CHECK (reused_from_plan_id IS NULL OR reused_from_plan_id <> id);

CREATE INDEX ix_cooking_plan_reuse_candidate
    ON public.cooking_plan (user_id, request_fingerprint, created_at DESC)
    WHERE status = 'READY' AND request_fingerprint IS NOT NULL;

COMMENT ON COLUMN public.cooking_plan.finished_at IS
    'When the user finished every client-side execution step and inventory was atomically consumed.';
COMMENT ON COLUMN public.cooking_plan.request_fingerprint IS
    'Stable public-input and kitchen-resource fingerprint used to reuse an equivalent READY schedule.';
COMMENT ON COLUMN public.cooking_plan.reused_from_plan_id IS
    'Previous READY plan whose materialised schedule was copied after current inventory revalidation.';
