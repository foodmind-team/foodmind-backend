ALTER TABLE public.cooking_plan
    ADD COLUMN saved_at timestamptz,
    ADD COLUMN execution_version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.cooking_plan
    ADD CONSTRAINT ck_cooking_plan_saved_ready
        CHECK (saved_at IS NULL OR status = 'READY'),
    ADD CONSTRAINT ck_cooking_plan_execution_version
        CHECK (execution_version >= 0);

CREATE INDEX ix_cooking_plan_user_saved
    ON public.cooking_plan (user_id, saved_at DESC, id DESC)
    WHERE saved_at IS NOT NULL;

CREATE TABLE public.cooking_plan_execution_step (
    plan_id    uuid         NOT NULL,
    step_id    varchar(160) NOT NULL,
    status     varchar(16)  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_cooking_plan_execution_step PRIMARY KEY (plan_id, step_id),
    CONSTRAINT fk_cooking_plan_execution_step_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_execution_step_id
        CHECK (step_id = btrim(step_id) AND step_id <> ''),
    CONSTRAINT ck_cooking_plan_execution_step_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_cooking_plan_execution_step_timestamp_order
        CHECK (updated_at >= created_at)
);

CREATE TRIGGER trg_cooking_plan_execution_step_set_updated_at
BEFORE UPDATE ON public.cooking_plan_execution_step
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();

COMMENT ON COLUMN public.cooking_plan.saved_at IS
    'Explicit user bookmark controlling whether this immutable plan appears under Saved > Cooking Plans.';
COMMENT ON COLUMN public.cooking_plan.execution_version IS
    'Owner-scoped optimistic concurrency version shared by Web and Android execution progress.';
COMMENT ON TABLE public.cooking_plan_execution_step IS
    'Mutable, account-synchronised execution progress for immutable READY plan steps.';
