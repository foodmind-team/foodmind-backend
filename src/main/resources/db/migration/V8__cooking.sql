-- Cooking is a durable workflow. The request is persisted before any remote
-- call and the returned recipe is copied into immutable child snapshots.

CREATE TABLE public.cooking_plan (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    source_recipe_id uuid,
    status varchar(30) NOT NULL DEFAULT 'CREATED',
    servings smallint NOT NULL,
    max_minutes integer,
    max_budget numeric(10, 2),
    currency char(3),
    request_context jsonb NOT NULL,
    agent_contract_version varchar(40),
    fallback_version varchar(40),
    fallback_status varchar(30) NOT NULL DEFAULT 'NOT_STARTED',
    correlation_id uuid NOT NULL,
    agent_trace_id varchar(128),
    failure_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,

    CONSTRAINT fk_cooking_plan_user
        FOREIGN KEY (user_id) REFERENCES public.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cooking_plan_source_recipe
        FOREIGN KEY (source_recipe_id) REFERENCES public.recipe (id) ON DELETE RESTRICT,
    CONSTRAINT ck_cooking_plan_status CHECK (
        status IN (
            'CREATED',
            'PROCESSING',
            'SUCCEEDED',
            'FALLBACK_SUCCEEDED',
            'NO_VALID_RECIPE',
            'FAILED'
        )
    ),
    CONSTRAINT ck_cooking_plan_servings CHECK (servings BETWEEN 1 AND 24),
    CONSTRAINT ck_cooking_plan_max_minutes CHECK (
        max_minutes IS NULL OR max_minutes BETWEEN 1 AND 1440
    ),
    CONSTRAINT ck_cooking_plan_budget_currency CHECK (
        (max_budget IS NULL AND currency IS NULL)
        OR (
            max_budget IS NOT NULL
            AND max_budget >= 0
            AND max_budget < 'Infinity'::numeric
            AND currency IS NOT NULL
            AND currency::text ~ '^[A-Z]{3}$'
        )
    ),
    CONSTRAINT ck_cooking_plan_request_context CHECK (
        jsonb_typeof(request_context) = 'object'
    ),
    CONSTRAINT ck_cooking_plan_fallback_status CHECK (
        fallback_status IN (
            'NOT_STARTED',
            'NOT_REQUIRED',
            'SUCCEEDED',
            'NO_VALID_RECIPE',
            'FAILED'
        )
    ),
    CONSTRAINT ck_cooking_plan_agent_contract_version CHECK (
        agent_contract_version IS NULL OR btrim(agent_contract_version) <> ''
    ),
    CONSTRAINT ck_cooking_plan_agent_trace_id CHECK (
        agent_trace_id IS NULL
        OR (
            agent_contract_version IS NOT NULL
            AND btrim(agent_trace_id) <> ''
        )
    ),
    CONSTRAINT ck_cooking_plan_fallback_version CHECK (
        (
            fallback_status IN ('NOT_STARTED', 'NOT_REQUIRED')
            AND fallback_version IS NULL
        )
        OR (
            fallback_status IN ('SUCCEEDED', 'NO_VALID_RECIPE', 'FAILED')
            AND fallback_version IS NOT NULL
            AND btrim(fallback_version) <> ''
        )
    ),
    CONSTRAINT ck_cooking_plan_failure_code CHECK (
        failure_code IS NULL OR failure_code ~ '^[A-Z][A-Z0-9_]{0,79}$'
    ),
    CONSTRAINT ck_cooking_plan_terminal_timestamp CHECK (
        (
            status IN ('CREATED', 'PROCESSING')
            AND completed_at IS NULL
        )
        OR (
            status IN (
                'SUCCEEDED',
                'FALLBACK_SUCCEEDED',
                'NO_VALID_RECIPE',
                'FAILED'
            )
            AND completed_at IS NOT NULL
            AND completed_at >= created_at
        )
    ),
    CONSTRAINT ck_cooking_plan_success_source CHECK (
        status NOT IN ('SUCCEEDED', 'FALLBACK_SUCCEEDED')
        OR source_recipe_id IS NOT NULL
    ),
    CONSTRAINT ck_cooking_plan_fallback_result CHECK (
        (status = 'CREATED' AND fallback_status = 'NOT_STARTED')
        OR (status = 'PROCESSING' AND fallback_status = 'NOT_STARTED')
        OR (status = 'SUCCEEDED' AND fallback_status = 'NOT_REQUIRED')
        OR (
            status = 'FALLBACK_SUCCEEDED'
            AND fallback_status = 'SUCCEEDED'
        )
        OR (
            status = 'NO_VALID_RECIPE'
            AND fallback_status = 'NO_VALID_RECIPE'
        )
        OR (
            status = 'FAILED'
            AND fallback_status IN ('NOT_STARTED', 'FAILED')
        )
    ),
    CONSTRAINT ck_cooking_plan_failure_presence CHECK (
        (
            status IN ('CREATED', 'PROCESSING', 'SUCCEEDED')
            AND failure_code IS NULL
        )
        OR status = 'FALLBACK_SUCCEEDED'
        OR (
            status IN ('NO_VALID_RECIPE', 'FAILED')
            AND failure_code IS NOT NULL
        )
    ),
    CONSTRAINT ck_cooking_plan_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_cooking_plan_version CHECK (version >= 0)
);

CREATE INDEX ix_cooking_plan_user_created
    ON public.cooking_plan (user_id, created_at DESC);

CREATE INDEX ix_cooking_plan_stale_workflow
    ON public.cooking_plan (status, created_at)
    WHERE status IN ('CREATED', 'PROCESSING');

CREATE INDEX ix_cooking_plan_source_recipe
    ON public.cooking_plan (source_recipe_id)
    WHERE source_recipe_id IS NOT NULL;

CREATE FUNCTION public.foodmind_guard_cooking_plan_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'cooking plans cannot be deleted';
    END IF;

    IF ROW(
        NEW.id,
        NEW.user_id,
        NEW.servings,
        NEW.max_minutes,
        NEW.max_budget,
        NEW.currency,
        NEW.request_context,
        NEW.correlation_id,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id,
        OLD.user_id,
        OLD.servings,
        OLD.max_minutes,
        OLD.max_budget,
        OLD.currency,
        OLD.request_context,
        OLD.correlation_id,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'cooking plan request identity and snapshot are immutable';
    END IF;

    IF OLD.status IN (
        'SUCCEEDED',
        'FALLBACK_SUCCEEDED',
        'NO_VALID_RECIPE',
        'FAILED'
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'terminal cooking plans are immutable';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
       AND NOT (
           (
               OLD.status = 'CREATED'
               AND NEW.status IN ('PROCESSING', 'FAILED')
           )
           OR
           (
               OLD.status = 'PROCESSING'
               AND NEW.status IN (
                   'SUCCEEDED',
                   'FALLBACK_SUCCEEDED',
                   'NO_VALID_RECIPE',
                   'FAILED'
               )
           )
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'invalid cooking plan status transition';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_cooking_plan_guard_mutation
    BEFORE UPDATE OR DELETE ON public.cooking_plan
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_guard_cooking_plan_mutation();

CREATE TRIGGER trg_cooking_plan_updated_at
    BEFORE UPDATE ON public.cooking_plan
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE public.cooking_plan_input (
    plan_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    ingredient_name varchar(160) NOT NULL,
    quantity numeric(12, 3),
    unit varchar(40),
    source varchar(30) NOT NULL,

    CONSTRAINT pk_cooking_plan_input PRIMARY KEY (plan_id, sequence_no),
    CONSTRAINT fk_cooking_plan_input_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_input_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_cooking_plan_input_name CHECK (btrim(ingredient_name) <> ''),
    CONSTRAINT ck_cooking_plan_input_quantity_unit CHECK (
        (quantity IS NULL AND unit IS NULL)
        OR (
            quantity IS NOT NULL
            AND quantity > 0
            AND quantity < 'Infinity'::numeric
            AND unit IS NOT NULL
            AND btrim(unit) <> ''
        )
    ),
    CONSTRAINT ck_cooking_plan_input_source CHECK (
        source IN ('MANUAL', 'AUTHORISED_PANTRY')
    )
);

CREATE TABLE public.cooking_plan_ingredient (
    plan_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    ingredient_name varchar(160) NOT NULL,
    quantity numeric(12, 3),
    unit varchar(40),
    availability varchar(20) NOT NULL,

    CONSTRAINT pk_cooking_plan_ingredient PRIMARY KEY (plan_id, sequence_no),
    CONSTRAINT fk_cooking_plan_ingredient_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_ingredient_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_cooking_plan_ingredient_name CHECK (btrim(ingredient_name) <> ''),
    CONSTRAINT ck_cooking_plan_ingredient_quantity_unit CHECK (
        (quantity IS NULL AND unit IS NULL)
        OR (
            quantity IS NOT NULL
            AND quantity > 0
            AND quantity < 'Infinity'::numeric
            AND unit IS NOT NULL
            AND btrim(unit) <> ''
        )
    ),
    CONSTRAINT ck_cooking_plan_ingredient_availability CHECK (
        availability IN ('AVAILABLE', 'TO_BUY')
    )
);

CREATE TABLE public.cooking_plan_step (
    plan_id uuid NOT NULL,
    step_no smallint NOT NULL,
    instruction text NOT NULL,

    CONSTRAINT pk_cooking_plan_step PRIMARY KEY (plan_id, step_no),
    CONSTRAINT fk_cooking_plan_step_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_step_number CHECK (step_no > 0),
    CONSTRAINT ck_cooking_plan_step_instruction CHECK (
        btrim(instruction) <> '' AND char_length(instruction) <= 4000
    )
);

CREATE TABLE public.cooking_plan_warning (
    plan_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    warning_code varchar(80) NOT NULL,
    message varchar(1000) NOT NULL,

    CONSTRAINT pk_cooking_plan_warning PRIMARY KEY (plan_id, sequence_no),
    CONSTRAINT fk_cooking_plan_warning_plan
        FOREIGN KEY (plan_id) REFERENCES public.cooking_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_cooking_plan_warning_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_cooking_plan_warning_code CHECK (
        warning_code ~ '^[A-Z][A-Z0-9_]{0,79}$'
    ),
    CONSTRAINT ck_cooking_plan_warning_message CHECK (btrim(message) <> '')
);

CREATE FUNCTION public.foodmind_guard_cooking_plan_input_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
    parent_status varchar(30);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'cooking plan input snapshots are immutable';
    END IF;

    SELECT plan.status
      INTO parent_status
      FROM public.cooking_plan AS plan
     WHERE plan.id = NEW.plan_id
     FOR UPDATE;

    IF parent_status IS DISTINCT FROM 'CREATED' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'cooking plan inputs may only be inserted while the plan is created';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE FUNCTION public.foodmind_guard_cooking_plan_output_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
    parent_status varchar(30);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'cooking plan output snapshots are immutable';
    END IF;

    SELECT plan.status
      INTO parent_status
      FROM public.cooking_plan AS plan
     WHERE plan.id = NEW.plan_id
     FOR UPDATE;

    IF parent_status IS DISTINCT FROM 'PROCESSING' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'cooking plan outputs may only be inserted while the plan is processing';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_cooking_plan_input_guard_mutation
    BEFORE INSERT OR UPDATE OR DELETE ON public.cooking_plan_input
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_guard_cooking_plan_input_mutation();

CREATE TRIGGER trg_cooking_plan_ingredient_guard_mutation
    BEFORE INSERT OR UPDATE OR DELETE ON public.cooking_plan_ingredient
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_guard_cooking_plan_output_mutation();

CREATE TRIGGER trg_cooking_plan_step_guard_mutation
    BEFORE INSERT OR UPDATE OR DELETE ON public.cooking_plan_step
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_guard_cooking_plan_output_mutation();

CREATE TRIGGER trg_cooking_plan_warning_guard_mutation
    BEFORE INSERT OR UPDATE OR DELETE ON public.cooking_plan_warning
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_guard_cooking_plan_output_mutation();

COMMENT ON TABLE public.cooking_plan_input IS
    'Append-only request snapshot inserted while the parent is CREATED, before transition to PROCESSING.';
COMMENT ON TABLE public.cooking_plan_ingredient IS
    'Append-only validated output snapshot inserted while the locked parent is PROCESSING.';
COMMENT ON TABLE public.cooking_plan_step IS
    'Append-only validated output snapshot inserted while the locked parent is PROCESSING.';
COMMENT ON TABLE public.cooking_plan_warning IS
    'Append-only validated output snapshot inserted while the locked parent is PROCESSING.';
