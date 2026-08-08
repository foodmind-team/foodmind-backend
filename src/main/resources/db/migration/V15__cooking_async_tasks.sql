-- Cooking Agent async task tracking (2026-08-03)
-- Async submission/polling against the agent task API
-- (/internal/v2/cooking-plan/tasks). The synchronous /generate path never
-- writes these columns/rows; only the async /generate-async chain does.

ALTER TABLE public.cooking_plan
    ADD COLUMN agent_task_id varchar(128);

CREATE TABLE public.cooking_plan_generation (
    plan_id             uuid          NOT NULL,
    agent_task_id       varchar(128)  NOT NULL,
    sync_state          varchar(32)   NOT NULL,            -- PENDING / POLLING / SUCCEEDED / FAILED / CANCELLED
    next_poll_at        timestamptz   NOT NULL,            -- lease: next time the row may be claimed
    attempt_count       integer       NOT NULL DEFAULT 0,  -- polling failures / interruptions so far
    last_error_code     varchar(64),
    last_progress_node  varchar(128),
    last_progress_steps integer       NOT NULL DEFAULT 0,
    last_progress_message text,
    created_at          timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_cooking_plan_generation PRIMARY KEY (plan_id),
    CONSTRAINT fk_cooking_plan_generation_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_generation_state CHECK (
        sync_state IN ('PENDING', 'POLLING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_cooking_plan_generation_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_cooking_plan_generation_steps CHECK (last_progress_steps >= 0)
);

CREATE INDEX ix_cooking_plan_generation_poll
    ON public.cooking_plan_generation (sync_state, next_poll_at);

CREATE FUNCTION public.foodmind_set_cooking_generation_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $function$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_cooking_plan_generation_set_updated_at
BEFORE UPDATE ON public.cooking_plan_generation
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_cooking_generation_updated_at();
