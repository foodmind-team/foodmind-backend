CREATE TABLE public.recipe_import_session (
    id                       uuid         NOT NULL,
    owner_user_id            uuid         NOT NULL,
    source_text              text         NOT NULL,
    status                   varchar(24)  NOT NULL DEFAULT 'PROCESSING',
    drafts_json              jsonb        NOT NULL DEFAULT '[]'::jsonb,
    questions_json           jsonb        NOT NULL DEFAULT '[]'::jsonb,
    answers_json             jsonb        NOT NULL DEFAULT '[]'::jsonb,
    created_recipe_ids_json  jsonb        NOT NULL DEFAULT '[]'::jsonb,
    failure_code             varchar(80),
    failure_message          varchar(240),
    created_at               timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at             timestamptz,
    version                  bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_recipe_import_session PRIMARY KEY (id),
    CONSTRAINT fk_recipe_import_session_owner
        FOREIGN KEY (owner_user_id) REFERENCES public.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_recipe_import_source_text CHECK (
        btrim(source_text) <> '' AND octet_length(source_text) <= 100000
    ),
    CONSTRAINT ck_recipe_import_status CHECK (
        status IN ('PROCESSING', 'NEEDS_CLARIFICATION', 'READY', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_recipe_import_json_arrays CHECK (
        jsonb_typeof(drafts_json) = 'array'
        AND jsonb_typeof(questions_json) = 'array'
        AND jsonb_typeof(answers_json) = 'array'
        AND jsonb_typeof(created_recipe_ids_json) = 'array'
    ),
    CONSTRAINT ck_recipe_import_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL AND jsonb_array_length(created_recipe_ids_json) > 0)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_recipe_import_failure CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL AND failure_message IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL AND failure_message IS NULL)
    ),
    CONSTRAINT ck_recipe_import_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_recipe_import_version CHECK (version >= 0)
);

CREATE INDEX ix_recipe_import_owner_updated
    ON public.recipe_import_session (owner_user_id, updated_at DESC, id DESC);

CREATE INDEX ix_recipe_import_owner_active
    ON public.recipe_import_session (owner_user_id, status, updated_at DESC)
    WHERE status IN ('PROCESSING', 'NEEDS_CLARIFICATION', 'READY');

CREATE TRIGGER trg_recipe_import_session_set_updated_at
BEFORE UPDATE ON public.recipe_import_session
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();
