-- Cooking Agent schema (2026-08-02)
-- Destructive: replaces the legacy V8 cooking_plan table family with the
-- agent-native schema. The Python agent never writes to this database;
-- these tables materialise its PlanResponse so the public API can serve it.

DROP TABLE IF EXISTS cooking_plan_warning, cooking_plan_step,
    cooking_plan_ingredient, cooking_plan_input, cooking_plan CASCADE;

-- ==========================================================================
-- Inventory catalogue & FEFO lots
-- ==========================================================================

CREATE TABLE public.inventory_item (
    id              uuid         NOT NULL,
    canonical_name  varchar(128) NOT NULL,
    default_unit    varchar(16)  NOT NULL DEFAULT 'g',
    category        varchar(64),
    high_risk       boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_inventory_item PRIMARY KEY (id),
    CONSTRAINT ck_inventory_item_name_not_blank
        CHECK (canonical_name = btrim(canonical_name) AND canonical_name <> ''),
    CONSTRAINT ck_inventory_item_default_unit
        CHECK (default_unit = btrim(default_unit) AND default_unit <> ''),
    CONSTRAINT ck_inventory_item_category
        CHECK (category IS NULL OR (category = btrim(category) AND category <> '')),
    CONSTRAINT ck_inventory_item_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_inventory_item_version CHECK (version >= 0)
);

COMMENT ON TABLE public.inventory_item IS
    'Canonical ingredient vocabulary owned by the user for inventory tracking.';
COMMENT ON COLUMN public.inventory_item.high_risk IS
    'Raw protein / perishable items that trigger food-safety handling rules.';

CREATE UNIQUE INDEX uq_inventory_item_name_ci
    ON public.inventory_item (lower(canonical_name));

CREATE TRIGGER trg_inventory_item_set_updated_at
BEFORE UPDATE ON public.inventory_item
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE public.inventory_lot (
    id            uuid          NOT NULL,
    item_id       uuid          NOT NULL,
    user_id       uuid          NOT NULL,
    on_hand       numeric(12,3) NOT NULL,
    reserved      numeric(12,3) NOT NULL DEFAULT 0,
    unit          varchar(16)   NOT NULL,
    expiry_date   date,
    purchased_at  timestamptz,
    created_at    timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       bigint        NOT NULL DEFAULT 0,

    CONSTRAINT pk_inventory_lot PRIMARY KEY (id),
    CONSTRAINT fk_inventory_lot_item
        FOREIGN KEY (item_id) REFERENCES public.inventory_item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_lot_user
        FOREIGN KEY (user_id) REFERENCES public.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_lot_on_hand CHECK (on_hand >= 0 AND on_hand < 'Infinity'::numeric),
    CONSTRAINT ck_inventory_lot_reserved CHECK (reserved >= 0),
    CONSTRAINT ck_inventory_lot_not_over_reserved CHECK (reserved <= on_hand),
    CONSTRAINT ck_inventory_lot_unit CHECK (unit = btrim(unit) AND unit <> ''),
    CONSTRAINT ck_inventory_lot_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_inventory_lot_version CHECK (version >= 0)
);

COMMENT ON TABLE public.inventory_lot IS
    'User inventory batches used for FEFO allocation. available = on_hand - reserved.';

CREATE INDEX ix_inventory_lot_available
    ON public.inventory_lot (item_id, user_id)
    WHERE (on_hand - reserved) > 0;

CREATE INDEX ix_inventory_lot_expiry
    ON public.inventory_lot (expiry_date)
    WHERE expiry_date IS NOT NULL;

CREATE INDEX ix_inventory_lot_fefo
    ON public.inventory_lot (item_id, user_id, expiry_date NULLS LAST, on_hand);

CREATE TRIGGER trg_inventory_lot_set_updated_at
BEFORE UPDATE ON public.inventory_lot
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();

-- ==========================================================================
-- Kitchen resources
-- ==========================================================================

CREATE TABLE public.kitchen_resource (
    id              uuid          NOT NULL,
    user_id         uuid          NOT NULL,
    resource_type   varchar(64)   NOT NULL,
    name            varchar(128),
    capacity        numeric(10,3),
    capacity_unit   varchar(16),
    capabilities    text[]        NOT NULL DEFAULT '{}',
    available       boolean       NOT NULL DEFAULT true,
    created_at      timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         bigint        NOT NULL DEFAULT 0,

    CONSTRAINT pk_kitchen_resource PRIMARY KEY (id),
    CONSTRAINT fk_kitchen_resource_user
        FOREIGN KEY (user_id) REFERENCES public.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_kitchen_resource_type CHECK (resource_type = btrim(resource_type) AND resource_type <> ''),
    CONSTRAINT ck_kitchen_resource_capacity_pair CHECK (
        (capacity IS NULL AND capacity_unit IS NULL)
        OR (capacity IS NOT NULL AND capacity_unit IS NOT NULL AND capacity_unit = btrim(capacity_unit) AND capacity_unit <> '')
    ),
    CONSTRAINT ck_kitchen_resource_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_kitchen_resource_version CHECK (version >= 0)
);

CREATE INDEX ix_kitchen_resource_user
    ON public.kitchen_resource (user_id, available);

CREATE TRIGGER trg_kitchen_resource_set_updated_at
BEFORE UPDATE ON public.kitchen_resource
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();

-- ==========================================================================
-- cooking_plan root (agent-native)
-- ==========================================================================

CREATE TABLE public.cooking_plan (
    id                          uuid          NOT NULL,
    user_id                     uuid          NOT NULL,
    status                      varchar(32)   NOT NULL,
    agent_request_id            varchar(128)  NOT NULL,
    plan_revision               varchar(64),
    region                      varchar(8),
    cooking_date                date,
    serving_at                  timestamptz,
    time_limit_minutes          integer,
    solver_status               varchar(32),
    makespan_minutes            integer,
    inventory_snapshot_version  varchar(64),
    correlation_id              varchar(128)  NOT NULL,
    agent_trace_id              varchar(128),
    schema_version              varchar(16)   NOT NULL DEFAULT '1.0',
    error_code                  varchar(64),
    error_message               text,
    request_context             jsonb         NOT NULL,
    response_json               jsonb,
    safety_policy_json          jsonb,
    created_at                  timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at                timestamptz,
    version                     bigint        NOT NULL DEFAULT 0,

    CONSTRAINT pk_cooking_plan PRIMARY KEY (id),
    CONSTRAINT fk_cooking_plan_user
        FOREIGN KEY (user_id) REFERENCES public.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT uq_cooking_plan_agent_request UNIQUE (agent_request_id),
    CONSTRAINT ck_cooking_plan_status CHECK (
        status IN ('PROCESSING', 'READY', 'NEEDS_CONFIRMATION', 'INFEASIBLE', 'FAILED')
    ),
    CONSTRAINT ck_cooking_plan_solver_status CHECK (
        solver_status IS NULL OR solver_status IN ('OPTIMAL', 'FEASIBLE', 'INFEASIBLE', 'UNKNOWN', 'MODEL_INVALID')
    ),
    CONSTRAINT ck_cooking_plan_makespan CHECK (makespan_minutes IS NULL OR makespan_minutes > 0),
    CONSTRAINT ck_cooking_plan_time_limit CHECK (
        time_limit_minutes IS NULL OR time_limit_minutes BETWEEN 1 AND 1440
    ),
    CONSTRAINT ck_cooking_plan_error_presence CHECK (
        (status = 'FAILED' AND error_code IS NOT NULL AND error_code <> '')
        OR (status <> 'FAILED' AND error_code IS NULL)
    ),
    CONSTRAINT ck_cooking_plan_terminal_timestamp CHECK (
        (status = 'PROCESSING' AND completed_at IS NULL)
        OR (status <> 'PROCESSING' AND completed_at IS NOT NULL AND completed_at >= created_at)
    ),
    CONSTRAINT ck_cooking_plan_context CHECK (jsonb_typeof(request_context) = 'object'),
    CONSTRAINT ck_cooking_plan_correlation CHECK (
        correlation_id ~ '^[A-Za-z0-9_-]{1,128}$'
    ),
    CONSTRAINT ck_cooking_plan_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_cooking_plan_version CHECK (version >= 0)
);

COMMENT ON TABLE public.cooking_plan IS
    'Agent-native cooking plan root. response_json is an audit copy; query reads materialised child tables.';

CREATE INDEX ix_cooking_plan_user_created
    ON public.cooking_plan (user_id, created_at DESC);

CREATE INDEX ix_cooking_plan_user_status
    ON public.cooking_plan (user_id, status, created_at DESC);

CREATE FUNCTION public.foodmind_guard_cooking_plan_mutation_v2()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE = '55000', MESSAGE = 'cooking plans cannot be deleted';
    END IF;
    IF NEW.status <> 'PROCESSING'
       AND OLD.status <> 'PROCESSING'
       AND OLD.status <> NEW.status THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'terminal cooking plans are immutable';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_cooking_plan_guard_mutation
    BEFORE UPDATE OR DELETE ON public.cooking_plan
    FOR EACH ROW EXECUTE FUNCTION public.foodmind_guard_cooking_plan_mutation_v2();

CREATE TRIGGER trg_cooking_plan_set_updated_at
BEFORE UPDATE ON public.cooking_plan
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();

-- ==========================================================================
-- cooking_plan children
-- ==========================================================================

CREATE TABLE public.cooking_plan_source (
    plan_id          uuid          NOT NULL,
    sequence_no      smallint      NOT NULL,
    source_type      varchar(16)   NOT NULL,
    source_id        uuid,
    target_servings  numeric(6,2)  NOT NULL,
    dish_name        varchar(256),
    recipe_text      text          NOT NULL,

    CONSTRAINT pk_cooking_plan_source PRIMARY KEY (plan_id, sequence_no),
    CONSTRAINT fk_cooking_plan_source_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_source_type CHECK (source_type IN ('CATALOGUE', 'OWNER')),
    CONSTRAINT ck_cooking_plan_source_order CHECK (sequence_no > 0),
    CONSTRAINT ck_cooking_plan_source_servings CHECK (target_servings > 0),
    CONSTRAINT ck_cooking_plan_source_text CHECK (btrim(recipe_text) <> '')
);

COMMENT ON TABLE public.cooking_plan_source IS
    'Immutable snapshot of each recipe text sent to the agent (reproducibility).';

CREATE INDEX ix_cooking_plan_source_plan
    ON public.cooking_plan_source (plan_id, sequence_no);

CREATE TABLE public.cooking_plan_task (
    plan_id                uuid          NOT NULL,
    task_id                varchar(128)  NOT NULL,
    dish_id                varchar(64)   NOT NULL,
    instruction            text          NOT NULL,
    duration_minutes       integer       NOT NULL,
    work_mode              varchar(16)   NOT NULL,
    category               varchar(64)   NOT NULL,
    heat_level             varchar(16)   NOT NULL DEFAULT 'NONE',
    target_temperature_c   numeric(5,1),
    start_minute           integer,
    end_minute             integer,
    resources              text[]        NOT NULL DEFAULT '{}',

    CONSTRAINT pk_cooking_plan_task PRIMARY KEY (plan_id, task_id),
    CONSTRAINT fk_cooking_plan_task_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_task_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_cooking_plan_task_mode CHECK (work_mode IN ('ACTIVE', 'PASSIVE')),
    CONSTRAINT ck_cooking_plan_task_heat CHECK (heat_level IN ('LOW', 'MEDIUM', 'HIGH', 'NONE')),
    CONSTRAINT ck_cooking_plan_task_window CHECK (
        (start_minute IS NULL AND end_minute IS NULL)
        OR (start_minute IS NOT NULL AND end_minute IS NOT NULL AND end_minute >= start_minute)
    )
);

CREATE INDEX ix_cooking_plan_task_timeline
    ON public.cooking_plan_task (plan_id, start_minute, end_minute);

CREATE TABLE public.cooking_plan_mise_en_place (
    plan_id            uuid          NOT NULL,
    sequence_no        smallint      NOT NULL,
    instruction        text          NOT NULL,
    ingredient         varchar(256),
    operation          varchar(64),
    duration_minutes   integer,
    resources          text[]        NOT NULL DEFAULT '{}',
    when_needed        varchar(128),

    CONSTRAINT pk_cooking_plan_mise PRIMARY KEY (plan_id, sequence_no),
    CONSTRAINT fk_cooking_plan_mise_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_mise_order CHECK (sequence_no > 0)
);

CREATE TABLE public.cooking_plan_dish_completion (
    plan_id            uuid          NOT NULL,
    dish_id            varchar(64)   NOT NULL,
    completion_minute  integer       NOT NULL,
    task_count         integer       NOT NULL,
    is_shared          boolean       NOT NULL DEFAULT false,

    CONSTRAINT pk_cooking_plan_dish PRIMARY KEY (plan_id, dish_id),
    CONSTRAINT fk_cooking_plan_dish_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE
);

CREATE TABLE public.cooking_plan_completion_item (
    id                   uuid          NOT NULL,
    plan_id              uuid          NOT NULL,
    completion_item_id   varchar(128)  NOT NULL,
    ingredient_name      varchar(256)  NOT NULL,
    recipe_ids           text[]        NOT NULL DEFAULT '{}',

    CONSTRAINT pk_cooking_plan_completion PRIMARY KEY (id),
    CONSTRAINT fk_cooking_plan_completion_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT uq_cooking_plan_completion UNIQUE (plan_id, completion_item_id)
);

CREATE INDEX ix_cooking_plan_completion_plan
    ON public.cooking_plan_completion_item (plan_id);

CREATE TABLE public.cooking_plan_lot_allocation (
    id                   uuid          NOT NULL,
    completion_item_id   uuid          NOT NULL,
    inventory_lot_id     uuid          NOT NULL,
    quantity             numeric(12,3) NOT NULL,
    unit                 varchar(16)   NOT NULL,
    is_reserved          boolean       NOT NULL DEFAULT false,

    CONSTRAINT pk_cooking_plan_lot_allocation PRIMARY KEY (id),
    CONSTRAINT fk_cooking_plan_allocation_completion
        FOREIGN KEY (completion_item_id)
        REFERENCES public.cooking_plan_completion_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_cooking_plan_allocation_lot
        FOREIGN KEY (inventory_lot_id) REFERENCES public.inventory_lot (id) ON DELETE RESTRICT,
    CONSTRAINT uq_cooking_plan_allocation UNIQUE (completion_item_id, inventory_lot_id),
    CONSTRAINT ck_cooking_plan_allocation_qty CHECK (quantity > 0 AND quantity < 'Infinity'::numeric),
    CONSTRAINT ck_cooking_plan_allocation_unit CHECK (unit = btrim(unit) AND unit <> '')
);

CREATE TABLE public.cooking_plan_assumption (
    plan_id        uuid           NOT NULL,
    sequence_no    smallint       NOT NULL,
    text           text           NOT NULL,
    confidence     numeric(4,3)   NOT NULL,
    source_type    varchar(32)    NOT NULL DEFAULT 'LLM_guess',
    evidence_url   text,

    CONSTRAINT pk_cooking_plan_assumption PRIMARY KEY (plan_id, sequence_no),
    CONSTRAINT fk_cooking_plan_assumption_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_assumption_confidence CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE public.cooking_plan_repair_option (
    plan_id               uuid          NOT NULL,
    option_id             varchar(128)  NOT NULL,
    option_type           varchar(32)   NOT NULL,
    description           text          NOT NULL,
    changes               text[]        NOT NULL DEFAULT '{}',
    effects               text[]        NOT NULL DEFAULT '{}',
    revalidation_status   varchar(32)   NOT NULL DEFAULT 'validated',

    CONSTRAINT pk_cooking_plan_repair PRIMARY KEY (plan_id, option_id),
    CONSTRAINT fk_cooking_plan_repair_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_repair_type CHECK (
        option_type IN ('substitute_ingredient', 'reduce_servings', 'alternative_equipment',
                        'replace_dish', 'extend_time', 'purchase')
    )
);

CREATE TABLE public.cooking_plan_confirmation_question (
    plan_id           uuid          NOT NULL,
    question_id       varchar(128)  NOT NULL,
    field_path        varchar(256)  NOT NULL,
    prompt            text          NOT NULL,
    response_type     varchar(16)   NOT NULL,
    options           jsonb         NOT NULL DEFAULT '[]'::jsonb,
    required          boolean       NOT NULL DEFAULT true,
    suggested_value   text,

    CONSTRAINT pk_cooking_plan_question PRIMARY KEY (plan_id, question_id),
    CONSTRAINT fk_cooking_plan_question_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_question_type CHECK (response_type IN ('CHOICE', 'TEXT')),
    CONSTRAINT ck_cooking_plan_question_options CHECK (jsonb_typeof(options) = 'array')
);

CREATE TABLE public.cooking_plan_decision (
    plan_id          uuid          NOT NULL,
    sequence_no      smallint      NOT NULL,
    option_id        varchar(128)  NOT NULL,
    option_type      varchar(32)   NOT NULL,
    payload          jsonb         NOT NULL DEFAULT '{}'::jsonb,
    plan_revision    varchar(64),

    CONSTRAINT pk_cooking_plan_decision PRIMARY KEY (plan_id, sequence_no),
    CONSTRAINT fk_cooking_plan_decision_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_decision_order CHECK (sequence_no > 0)
);

CREATE TABLE public.cooking_plan_safety_finding (
    plan_id                  uuid          NOT NULL,
    rule_id                  varchar(64)   NOT NULL,
    severity                 varchar(32)   NOT NULL,
    description              text          NOT NULL,
    affected_task_ids        text[]        NOT NULL DEFAULT '{}',
    affected_ingredients     text[]        NOT NULL DEFAULT '{}',
    recommended_action       text,

    CONSTRAINT pk_cooking_plan_safety PRIMARY KEY (plan_id, rule_id),
    CONSTRAINT fk_cooking_plan_safety_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_safety_severity CHECK (
        severity IN ('hard_unrepairable', 'hard_repairable', 'warning')
    )
);

-- ==========================================================================
-- Agent request audit
-- ==========================================================================

CREATE TABLE public.agent_request (
    id                    uuid          NOT NULL,
    request_id            varchar(128)  NOT NULL,
    user_id               uuid          NOT NULL,
    plan_id               uuid,
    correlation_id        varchar(128)  NOT NULL,
    schema_version        varchar(16)   NOT NULL DEFAULT '1.0',
    recipe_count          integer       NOT NULL,
    task_count            integer,
    status                varchar(32)   NOT NULL,
    solver_status         varchar(32),
    makespan_minutes      integer,
    total_duration_ms     integer,
    error_code            varchar(64),
    created_at            timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_agent_request PRIMARY KEY (id),
    CONSTRAINT fk_agent_request_user
        FOREIGN KEY (user_id) REFERENCES public.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_request_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE SET NULL,
    CONSTRAINT uq_agent_request_request_id UNIQUE (request_id)
);

CREATE INDEX ix_agent_request_user_created
    ON public.agent_request (user_id, created_at DESC);

CREATE INDEX ix_agent_request_plan
    ON public.agent_request (plan_id);
