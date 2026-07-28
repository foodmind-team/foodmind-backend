-- Chat history is owner-scoped. References record what was shared, but every
-- later resolution still requires live source authorisation in the query path.

CREATE TABLE public.chat_session (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    title varchar(160),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,

    CONSTRAINT fk_chat_session_user
        FOREIGN KEY (user_id) REFERENCES public.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_chat_session_title CHECK (title IS NULL OR btrim(title) <> ''),
    CONSTRAINT ck_chat_session_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_chat_session_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_chat_session_version CHECK (version >= 0)
);

CREATE INDEX ix_chat_session_user_updated
    ON public.chat_session (user_id, updated_at DESC, id);

CREATE INDEX ix_chat_session_user_active
    ON public.chat_session (user_id, updated_at DESC, id)
    WHERE status = 'ACTIVE';

CREATE TRIGGER trg_chat_session_updated_at
    BEFORE UPDATE ON public.chat_session
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE public.chat_message (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL,
    role varchar(20) NOT NULL,
    content text NOT NULL,
    route varchar(40),
    response_status varchar(30),
    correlation_id uuid NOT NULL,
    agent_trace_id varchar(128),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_chat_message_session_id UNIQUE (session_id, id),
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id) REFERENCES public.chat_session (id) ON DELETE RESTRICT,
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_chat_message_content CHECK (
        btrim(content) <> '' AND char_length(content) <= 12000
    ),
    CONSTRAINT ck_chat_message_route CHECK (
        route IS NULL
        OR route IN (
            'SEARCH',
            'SUMMARY',
            'COMPARE',
            'NAVIGATION',
            'OUT_OF_SCOPE'
        )
    ),
    CONSTRAINT ck_chat_message_response_status CHECK (
        response_status IS NULL
        OR response_status IN (
            'SUCCEEDED',
            'FALLBACK_SUCCEEDED',
            'UNSUPPORTED',
            'FAILED'
        )
    ),
    CONSTRAINT ck_chat_message_role_metadata CHECK (
        (
            role = 'USER'
            AND route IS NULL
            AND response_status IS NULL
            AND agent_trace_id IS NULL
        )
        OR (
            role = 'ASSISTANT'
            AND route IS NOT NULL
            AND response_status IS NOT NULL
        )
    ),
    CONSTRAINT ck_chat_message_agent_trace_id CHECK (
        agent_trace_id IS NULL OR btrim(agent_trace_id) <> ''
    )
);

CREATE INDEX ix_chat_message_session_created
    ON public.chat_message (session_id, created_at, id);

CREATE INDEX ix_chat_message_correlation
    ON public.chat_message (correlation_id);

CREATE TABLE public.chat_reference (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL,
    origin varchar(24) NOT NULL,
    introduced_by_message_id uuid,
    source_type varchar(30) NOT NULL,
    food_record_id uuid,
    food_product_id uuid,
    place_id uuid,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_chat_reference_session_id UNIQUE (session_id, id),
    CONSTRAINT fk_chat_reference_session
        FOREIGN KEY (session_id) REFERENCES public.chat_session (id) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_reference_introducing_message
        FOREIGN KEY (session_id, introduced_by_message_id)
        REFERENCES public.chat_message (session_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_reference_food_record
        FOREIGN KEY (food_record_id) REFERENCES public.food_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_reference_food_product
        FOREIGN KEY (food_product_id) REFERENCES public.food_product (id) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_reference_place
        FOREIGN KEY (place_id) REFERENCES public.place (id) ON DELETE RESTRICT,
    CONSTRAINT ck_chat_reference_source_type CHECK (
        source_type IN ('FOOD_RECORD', 'FOOD_PRODUCT', 'PLACE')
    ),
    CONSTRAINT ck_chat_reference_origin CHECK (
        origin IN ('USER_SHARED', 'MESSAGE_INTRODUCED')
    ),
    CONSTRAINT ck_chat_reference_origin_message CHECK (
        (origin = 'USER_SHARED' AND introduced_by_message_id IS NULL)
        OR (
            origin = 'MESSAGE_INTRODUCED'
            AND introduced_by_message_id IS NOT NULL
        )
    ),
    CONSTRAINT ck_chat_reference_exact_source CHECK (
        num_nonnulls(food_record_id, food_product_id, place_id) = 1
    ),
    CONSTRAINT ck_chat_reference_source_matches_type CHECK (
        (source_type = 'FOOD_RECORD' AND food_record_id IS NOT NULL)
        OR (source_type = 'FOOD_PRODUCT' AND food_product_id IS NOT NULL)
        OR (source_type = 'PLACE' AND place_id IS NOT NULL)
    )
);

CREATE INDEX ix_chat_reference_session_created
    ON public.chat_reference (session_id, created_at, id);

CREATE INDEX ix_chat_reference_food_record
    ON public.chat_reference (food_record_id)
    WHERE food_record_id IS NOT NULL;

CREATE INDEX ix_chat_reference_food_product
    ON public.chat_reference (food_product_id)
    WHERE food_product_id IS NOT NULL;

CREATE INDEX ix_chat_reference_place
    ON public.chat_reference (place_id)
    WHERE place_id IS NOT NULL;

CREATE UNIQUE INDEX uq_chat_reference_session_food_record
    ON public.chat_reference (session_id, food_record_id)
    WHERE food_record_id IS NOT NULL;

CREATE UNIQUE INDEX uq_chat_reference_session_food_product
    ON public.chat_reference (session_id, food_product_id)
    WHERE food_product_id IS NOT NULL;

CREATE UNIQUE INDEX uq_chat_reference_session_place
    ON public.chat_reference (session_id, place_id)
    WHERE place_id IS NOT NULL;

CREATE TABLE public.chat_message_source (
    session_id uuid NOT NULL,
    message_id uuid NOT NULL,
    reference_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    grounding_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT pk_chat_message_source PRIMARY KEY (message_id, sequence_no),
    CONSTRAINT uq_chat_message_source_reference UNIQUE (message_id, reference_id),
    CONSTRAINT fk_chat_message_source_message
        FOREIGN KEY (session_id, message_id)
        REFERENCES public.chat_message (session_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_source_reference
        FOREIGN KEY (session_id, reference_id)
        REFERENCES public.chat_reference (session_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_chat_message_source_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_chat_message_source_grounding CHECK (
        jsonb_typeof(grounding_metadata) = 'object'
    )
);

CREATE INDEX ix_chat_message_source_reference
    ON public.chat_message_source (reference_id, message_id);
