-- Versioned recommendation workflow, evaluated candidate evidence, explanations,
-- append-only feedback, and recommendation sharing.

CREATE TABLE public.recommendation_session (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    group_id uuid,
    parent_session_id uuid,
    status varchar(32) NOT NULL DEFAULT 'CREATED',
    meal_type varchar(40),
    max_budget numeric(10,2),
    currency char(3),
    area varchar(120),
    latitude numeric(9,6),
    longitude numeric(9,6),
    max_distance_km numeric(6,2),
    mood varchar(120),
    requested_for timestamptz,
    request_context jsonb NOT NULL,
    public_contract_version varchar(40) NOT NULL,
    agent_contract_version varchar(40),
    model_version varchar(80),
    model_status varchar(32) NOT NULL DEFAULT 'NOT_REQUESTED',
    fallback_version varchar(40),
    fallback_status varchar(32) NOT NULL DEFAULT 'NOT_STARTED',
    correlation_id uuid NOT NULL,
    agent_trace_id varchar(128),
    failure_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at timestamptz,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,

    CONSTRAINT fk_recommendation_session_user
        FOREIGN KEY (user_id)
        REFERENCES public.app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_recommendation_session_group
        FOREIGN KEY (group_id)
        REFERENCES public.trusted_group (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_recommendation_session_id_user
        UNIQUE (id, user_id),
    CONSTRAINT fk_recommendation_session_parent_owner
        FOREIGN KEY (parent_session_id, user_id)
        REFERENCES public.recommendation_session (id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_recommendation_session_not_own_parent
        CHECK (parent_session_id IS NULL OR parent_session_id <> id),
    CONSTRAINT ck_recommendation_session_status
        CHECK (
            status IN (
                'CREATED',
                'PROCESSING',
                'SUCCEEDED',
                'FALLBACK_SUCCEEDED',
                'NO_VALID_CANDIDATE',
                'FAILED'
            )
        ),
    CONSTRAINT ck_recommendation_session_meal_type
        CHECK (meal_type IS NULL OR btrim(meal_type) <> ''),
    CONSTRAINT ck_recommendation_session_money
        CHECK (
            (max_budget IS NULL AND currency IS NULL)
            OR
            (
                max_budget IS NOT NULL
                AND max_budget >= 0
                AND max_budget < 'Infinity'::numeric
                AND currency IS NOT NULL
                AND currency::text ~ '^[A-Z]{3}$'
            )
        ),
    CONSTRAINT ck_recommendation_session_coordinates
        CHECK (
            (latitude IS NULL AND longitude IS NULL)
            OR
            (
                latitude IS NOT NULL
                AND longitude IS NOT NULL
                AND latitude BETWEEN -90 AND 90
                AND longitude BETWEEN -180 AND 180
            )
        ),
    CONSTRAINT ck_recommendation_session_distance
        CHECK (
            max_distance_km IS NULL
            OR (
                max_distance_km > 0
                AND max_distance_km < 'Infinity'::numeric
                AND latitude IS NOT NULL
                AND longitude IS NOT NULL
            )
        ),
    CONSTRAINT ck_recommendation_session_request_context
        CHECK (jsonb_typeof(request_context) = 'object'),
    CONSTRAINT ck_recommendation_session_public_contract
        CHECK (btrim(public_contract_version) <> ''),
    CONSTRAINT ck_recommendation_session_agent_contract
        CHECK (
            agent_contract_version IS NULL
            OR btrim(agent_contract_version) <> ''
        ),
    CONSTRAINT ck_recommendation_session_model_status
        CHECK (
            model_status IN (
                'NOT_REQUESTED',
                'PENDING',
                'SUCCEEDED',
                'INSUFFICIENT_DATA',
                'UNAVAILABLE',
                'TIMED_OUT',
                'INVALID_RESPONSE',
                'FAILED'
            )
        ),
    CONSTRAINT ck_recommendation_session_fallback_status
        CHECK (
            fallback_status IN (
                'NOT_STARTED',
                'NOT_REQUIRED',
                'SUCCEEDED',
                'NO_VALID_CANDIDATE',
                'FAILED'
            )
        ),
    CONSTRAINT ck_recommendation_session_model_metadata
        CHECK (
            model_status <> 'SUCCEEDED'
            OR (model_version IS NOT NULL AND btrim(model_version) <> '')
        ),
    CONSTRAINT ck_recommendation_session_fallback_metadata
        CHECK (
            fallback_status IN ('NOT_STARTED', 'NOT_REQUIRED')
            OR (fallback_version IS NOT NULL AND btrim(fallback_version) <> '')
        ),
    CONSTRAINT ck_recommendation_session_agent_trace
        CHECK (
            agent_trace_id IS NULL
            OR (
                agent_contract_version IS NOT NULL
                AND btrim(agent_trace_id) <> ''
            )
        ),
    CONSTRAINT ck_recommendation_session_failure_code
        CHECK (
            failure_code IS NULL
            OR failure_code ~ '^[A-Z][A-Z0-9_]{0,79}$'
        ),
    CONSTRAINT ck_recommendation_session_agent_attempt
        CHECK (
            model_status = 'NOT_REQUESTED'
            OR agent_contract_version IS NOT NULL
        ),
    CONSTRAINT ck_recommendation_session_result_coherence
        CHECK (
            (
                status = 'CREATED'
                AND model_status = 'NOT_REQUESTED'
                AND fallback_status = 'NOT_STARTED'
            )
            OR
            (
                status = 'PROCESSING'
                AND model_status IN ('NOT_REQUESTED', 'PENDING')
                AND fallback_status = 'NOT_STARTED'
            )
            OR
            (
                status = 'SUCCEEDED'
                AND
                model_status = 'SUCCEEDED'
                AND fallback_status = 'NOT_REQUIRED'
            )
            OR
            (
                status = 'FALLBACK_SUCCEEDED'
                AND model_status IN (
                    'NOT_REQUESTED',
                    'INSUFFICIENT_DATA',
                    'UNAVAILABLE',
                    'TIMED_OUT',
                    'INVALID_RESPONSE',
                    'FAILED'
                )
                AND fallback_status = 'SUCCEEDED'
            )
            OR
            (
                status = 'NO_VALID_CANDIDATE'
                AND model_status IN (
                    'NOT_REQUESTED',
                    'INSUFFICIENT_DATA',
                    'UNAVAILABLE',
                    'TIMED_OUT',
                    'INVALID_RESPONSE',
                    'FAILED'
                )
                AND fallback_status = 'NO_VALID_CANDIDATE'
            )
            OR
            (
                status = 'FAILED'
                AND model_status IN (
                    'NOT_REQUESTED',
                    'INSUFFICIENT_DATA',
                    'UNAVAILABLE',
                    'TIMED_OUT',
                    'INVALID_RESPONSE',
                    'FAILED'
                )
                AND fallback_status IN ('NOT_STARTED', 'FAILED')
            )
        ),
    CONSTRAINT ck_recommendation_session_failure_presence
        CHECK (
            (
                status IN ('CREATED', 'PROCESSING', 'SUCCEEDED')
                AND failure_code IS NULL
            )
            OR
            (
                status IN (
                    'FALLBACK_SUCCEEDED',
                    'NO_VALID_CANDIDATE'
                )
            )
            OR
            (
                status = 'FAILED'
                AND failure_code IS NOT NULL
            )
        ),
    CONSTRAINT ck_recommendation_session_lifecycle
        CHECK (
            (
                status = 'CREATED'
                AND started_at IS NULL
                AND completed_at IS NULL
            )
            OR
            (
                status = 'PROCESSING'
                AND started_at IS NOT NULL
                AND completed_at IS NULL
            )
            OR
            (
                status IN (
                    'SUCCEEDED',
                    'FALLBACK_SUCCEEDED',
                    'NO_VALID_CANDIDATE',
                    'FAILED'
                )
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
            )
        ),
    CONSTRAINT ck_recommendation_session_timestamps
        CHECK (
            updated_at >= created_at
            AND (started_at IS NULL OR started_at >= created_at)
            AND (completed_at IS NULL OR completed_at >= started_at)
        ),
    CONSTRAINT ck_recommendation_session_version
        CHECK (version >= 0)
);

CREATE INDEX ix_recommendation_session_user_created
    ON public.recommendation_session (user_id, created_at DESC, id);

CREATE INDEX ix_recommendation_session_group_created
    ON public.recommendation_session (group_id, created_at DESC)
    WHERE group_id IS NOT NULL;

CREATE INDEX ix_recommendation_session_status_created
    ON public.recommendation_session (status, created_at);

CREATE INDEX ix_recommendation_session_parent
    ON public.recommendation_session (parent_session_id)
    WHERE parent_session_id IS NOT NULL;

CREATE INDEX ix_recommendation_session_processing_started
    ON public.recommendation_session (started_at)
    WHERE status = 'PROCESSING';

CREATE TRIGGER trg_recommendation_session_set_updated_at
    BEFORE UPDATE ON public.recommendation_session
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE FUNCTION public.foodmind_guard_recommendation_session_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'recommendation sessions cannot be deleted';
    END IF;

    IF ROW(
        NEW.id,
        NEW.user_id,
        NEW.group_id,
        NEW.parent_session_id,
        NEW.meal_type,
        NEW.max_budget,
        NEW.currency,
        NEW.area,
        NEW.latitude,
        NEW.longitude,
        NEW.max_distance_km,
        NEW.mood,
        NEW.requested_for,
        NEW.request_context,
        NEW.public_contract_version,
        NEW.correlation_id,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id,
        OLD.user_id,
        OLD.group_id,
        OLD.parent_session_id,
        OLD.meal_type,
        OLD.max_budget,
        OLD.currency,
        OLD.area,
        OLD.latitude,
        OLD.longitude,
        OLD.max_distance_km,
        OLD.mood,
        OLD.requested_for,
        OLD.request_context,
        OLD.public_contract_version,
        OLD.correlation_id,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'recommendation session request identity and snapshot are immutable';
    END IF;

    IF OLD.status IN (
        'SUCCEEDED',
        'FALLBACK_SUCCEEDED',
        'NO_VALID_CANDIDATE',
        'FAILED'
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'terminal recommendation sessions are immutable';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
       AND NOT (
           (
               OLD.status = 'CREATED'
               AND NEW.status = 'PROCESSING'
           )
           OR
           (
               OLD.status = 'PROCESSING'
               AND NEW.status IN (
                   'SUCCEEDED',
                   'FALLBACK_SUCCEEDED',
                   'NO_VALID_CANDIDATE',
                   'FAILED'
               )
           )
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'invalid recommendation session status transition';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_recommendation_session_guard_mutation
    BEFORE UPDATE OR DELETE ON public.recommendation_session
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_guard_recommendation_session_mutation();


CREATE TABLE public.recommendation_candidate (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL,
    place_meal_id uuid NOT NULL,
    eligibility_status varchar(20) NOT NULL,
    filter_code varchar(80),
    model_score numeric(8,7),
    fallback_score numeric(8,7),
    feature_schema_version varchar(40),
    feature_snapshot jsonb,
    candidate_type varchar(24),
    rank smallint,
    evidence_snapshot jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,

    CONSTRAINT fk_recommendation_candidate_session
        FOREIGN KEY (session_id)
        REFERENCES public.recommendation_session (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_recommendation_candidate_place_meal
        FOREIGN KEY (place_meal_id)
        REFERENCES public.place_meal (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_recommendation_candidate_session_place_meal
        UNIQUE (session_id, place_meal_id),
    CONSTRAINT uq_recommendation_candidate_id_session
        UNIQUE (id, session_id),
    CONSTRAINT ck_recommendation_candidate_eligibility
        CHECK (eligibility_status IN ('FILTERED', 'ELIGIBLE', 'RETURNED')),
    CONSTRAINT ck_recommendation_candidate_filter
        CHECK (
            (eligibility_status = 'FILTERED' AND filter_code IS NOT NULL)
            OR
            (eligibility_status <> 'FILTERED' AND filter_code IS NULL)
        ),
    CONSTRAINT ck_recommendation_candidate_filter_code
        CHECK (filter_code IS NULL OR btrim(filter_code) <> ''),
    CONSTRAINT ck_recommendation_candidate_model_score
        CHECK (model_score IS NULL OR model_score BETWEEN 0 AND 1),
    CONSTRAINT ck_recommendation_candidate_fallback_score
        CHECK (fallback_score IS NULL OR fallback_score BETWEEN 0 AND 1),
    CONSTRAINT ck_recommendation_candidate_feature_snapshot
        CHECK (
            (
                feature_schema_version IS NULL
                AND feature_snapshot IS NULL
            )
            OR
            (
                feature_schema_version IS NOT NULL
                AND btrim(feature_schema_version) <> ''
                AND feature_snapshot IS NOT NULL
                AND jsonb_typeof(feature_snapshot) = 'object'
            )
        ),
    CONSTRAINT ck_recommendation_candidate_type
        CHECK (
            candidate_type IS NULL
            OR candidate_type IN ('PERSONAL', 'EXPLORATORY', 'GROUP_INSPIRED')
        ),
    CONSTRAINT ck_recommendation_candidate_returned_fields
        CHECK (
            (
                eligibility_status = 'RETURNED'
                AND candidate_type IS NOT NULL
                AND rank IS NOT NULL
                AND rank BETWEEN 1 AND 3
            )
            OR
            (
                eligibility_status <> 'RETURNED'
                AND candidate_type IS NULL
                AND rank IS NULL
            )
        ),
    CONSTRAINT ck_recommendation_candidate_evidence
        CHECK (jsonb_typeof(evidence_snapshot) = 'object'),
    CONSTRAINT ck_recommendation_candidate_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_recommendation_candidate_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_recommendation_candidate_session_rank
    ON public.recommendation_candidate (session_id, rank)
    WHERE rank IS NOT NULL;

CREATE INDEX ix_recommendation_candidate_session_eligibility
    ON public.recommendation_candidate (session_id, eligibility_status, id);

CREATE INDEX ix_recommendation_candidate_place_meal_created
    ON public.recommendation_candidate (place_meal_id, created_at DESC);

CREATE TRIGGER trg_recommendation_candidate_set_updated_at
    BEFORE UPDATE ON public.recommendation_candidate
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE FUNCTION public.foodmind_guard_recommendation_candidate_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
    parent_status varchar(32);
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'recommendation candidates cannot be deleted';
    END IF;

    IF TG_OP = 'INSERT' THEN
        SELECT session.status
          INTO parent_status
          FROM public.recommendation_session AS session
         WHERE session.id = NEW.session_id
         FOR UPDATE;

        IF parent_status IS NULL
           OR parent_status NOT IN ('CREATED', 'PROCESSING') THEN
            RAISE EXCEPTION USING
                ERRCODE = '55000',
                MESSAGE = 'recommendation candidates may only be inserted while the session is being assembled';
        END IF;

        RETURN NEW;
    END IF;

    SELECT session.status
      INTO parent_status
      FROM public.recommendation_session AS session
     WHERE session.id = OLD.session_id
     FOR UPDATE;

    IF parent_status IS DISTINCT FROM 'PROCESSING' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'recommendation candidates may only be advanced by a processing session';
    END IF;

    IF ROW(
        NEW.id,
        NEW.session_id,
        NEW.place_meal_id,
        NEW.feature_schema_version,
        NEW.feature_snapshot,
        NEW.evidence_snapshot,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id,
        OLD.session_id,
        OLD.place_meal_id,
        OLD.feature_schema_version,
        OLD.feature_snapshot,
        OLD.evidence_snapshot,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'recommendation candidate identity and evidence snapshots are immutable';
    END IF;

    IF OLD.eligibility_status IN ('FILTERED', 'RETURNED') THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'filtered and returned recommendation candidates are immutable';
    END IF;

    IF NEW.eligibility_status IS DISTINCT FROM OLD.eligibility_status
       AND NOT (
           OLD.eligibility_status = 'ELIGIBLE'
           AND NEW.eligibility_status = 'RETURNED'
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'invalid recommendation candidate eligibility transition';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_recommendation_candidate_guard_mutation
    BEFORE INSERT OR UPDATE OR DELETE ON public.recommendation_candidate
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_guard_recommendation_candidate_mutation();


CREATE TABLE public.candidate_reason (
    candidate_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    reason_code varchar(50) NOT NULL,
    evidence_json jsonb NOT NULL,

    CONSTRAINT pk_candidate_reason
        PRIMARY KEY (candidate_id, sequence_no),
    CONSTRAINT fk_candidate_reason_candidate
        FOREIGN KEY (candidate_id)
        REFERENCES public.recommendation_candidate (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_candidate_reason_sequence
        CHECK (sequence_no > 0),
    CONSTRAINT ck_candidate_reason_code
        CHECK (
            reason_code IN (
                'CUISINE_MATCH',
                'WITHIN_BUDGET',
                'SPICE_MATCH',
                'NEARBY',
                'NOT_RECENTLY_REPEATED',
                'SIMILAR_USERS_LIKED',
                'SIMILAR_TO_LIKED_MEALS',
                'TRUSTED_GROUP_RATING',
                'WANT_TO_TRY'
            )
        ),
    CONSTRAINT ck_candidate_reason_evidence
        CHECK (
            jsonb_typeof(evidence_json) = 'object'
            AND evidence_json <> '{}'::jsonb
        )
);

CREATE FUNCTION public.foodmind_guard_candidate_reason_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
    parent_candidate_status varchar(20);
    parent_session_status varchar(32);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT candidate.eligibility_status, session.status
          INTO parent_candidate_status, parent_session_status
          FROM public.recommendation_candidate AS candidate
          JOIN public.recommendation_session AS session
            ON session.id = candidate.session_id
         WHERE candidate.id = NEW.candidate_id
         FOR UPDATE OF candidate, session;

        IF parent_candidate_status IS DISTINCT FROM 'ELIGIBLE'
           OR parent_session_status IS DISTINCT FROM 'PROCESSING' THEN
            RAISE EXCEPTION USING
                ERRCODE = '55000',
                MESSAGE = 'candidate reasons may only be inserted before a processing session returns the candidate';
        END IF;

        RETURN NEW;
    END IF;

    RAISE EXCEPTION USING
        ERRCODE = '55000',
        MESSAGE = 'candidate_reason is immutable';
END;
$function$;

CREATE TRIGGER trg_candidate_reason_guard_mutation
    BEFORE INSERT OR UPDATE OR DELETE ON public.candidate_reason
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_guard_candidate_reason_mutation();


CREATE TABLE public.recommendation_feedback (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL,
    candidate_id uuid,
    user_id uuid NOT NULL,
    event_type varchar(32) NOT NULL,
    reason_code varchar(80),
    rating numeric(2,1),
    boolean_value boolean,
    resulting_food_record_id uuid,
    effective_until timestamptz,
    idempotency_key varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recommendation_feedback_session_owner
        FOREIGN KEY (session_id, user_id)
        REFERENCES public.recommendation_session (id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_recommendation_feedback_candidate_session
        FOREIGN KEY (candidate_id, session_id)
        REFERENCES public.recommendation_candidate (id, session_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_recommendation_feedback_result_record_owner
        FOREIGN KEY (resulting_food_record_id, user_id)
        REFERENCES public.food_record (id, owner_user_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_recommendation_feedback_user_idempotency
        UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_recommendation_feedback_event_type
        CHECK (
            event_type IN (
                'ACCEPTED',
                'REJECTED',
                'RERECOMMEND_REQUESTED',
                'LATER_RATED',
                'WOULD_EAT_AGAIN'
            )
        ),
    CONSTRAINT ck_recommendation_feedback_reason
        CHECK (
            reason_code IS NULL
            OR (btrim(reason_code) <> '' AND event_type = 'REJECTED')
        ),
    CONSTRAINT ck_recommendation_feedback_rating
        CHECK (rating IS NULL OR rating BETWEEN 1.0 AND 5.0),
    CONSTRAINT ck_recommendation_feedback_idempotency_key
        CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_recommendation_feedback_effective_until
        CHECK (
            effective_until IS NULL
            OR (
                event_type = 'REJECTED'
                AND effective_until > created_at
            )
        ),
    CONSTRAINT ck_recommendation_feedback_payload
        CHECK (
            (
                event_type = 'ACCEPTED'
                AND candidate_id IS NOT NULL
                AND reason_code IS NULL
                AND rating IS NULL
                AND boolean_value IS NULL
                AND resulting_food_record_id IS NULL
                AND effective_until IS NULL
            )
            OR
            (
                event_type = 'REJECTED'
                AND candidate_id IS NOT NULL
                AND rating IS NULL
                AND boolean_value IS NULL
                AND resulting_food_record_id IS NULL
            )
            OR
            (
                event_type = 'RERECOMMEND_REQUESTED'
                AND candidate_id IS NULL
                AND reason_code IS NULL
                AND rating IS NULL
                AND boolean_value IS NULL
                AND resulting_food_record_id IS NULL
                AND effective_until IS NULL
            )
            OR
            (
                event_type = 'LATER_RATED'
                AND candidate_id IS NOT NULL
                AND reason_code IS NULL
                AND rating IS NOT NULL
                AND boolean_value IS NULL
                AND effective_until IS NULL
            )
            OR
            (
                event_type = 'WOULD_EAT_AGAIN'
                AND candidate_id IS NOT NULL
                AND reason_code IS NULL
                AND rating IS NULL
                AND boolean_value IS NOT NULL
                AND effective_until IS NULL
            )
        )
);

CREATE INDEX ix_recommendation_feedback_session_created
    ON public.recommendation_feedback (session_id, created_at, id);

CREATE INDEX ix_recommendation_feedback_candidate_created
    ON public.recommendation_feedback (candidate_id, created_at)
    WHERE candidate_id IS NOT NULL;

CREATE INDEX ix_recommendation_feedback_result_record
    ON public.recommendation_feedback (resulting_food_record_id)
    WHERE resulting_food_record_id IS NOT NULL;

CREATE INDEX ix_recommendation_feedback_explicit_label
    ON public.recommendation_feedback (event_type, created_at)
    WHERE event_type IN ('ACCEPTED', 'REJECTED');

CREATE FUNCTION public.foodmind_reject_feedback_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '55000',
        MESSAGE = 'recommendation_feedback is append-only';
END;
$function$;

CREATE TRIGGER trg_recommendation_feedback_append_only
    BEFORE UPDATE OR DELETE ON public.recommendation_feedback
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_reject_feedback_mutation();


CREATE TABLE public.group_recommendation_share (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL,
    shared_by_user_id uuid NOT NULL,
    recommendation_candidate_id uuid NOT NULL,
    message text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,

    CONSTRAINT fk_group_recommendation_share_group
        FOREIGN KEY (group_id)
        REFERENCES public.trusted_group (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_group_recommendation_share_user
        FOREIGN KEY (shared_by_user_id)
        REFERENCES public.app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_group_recommendation_share_candidate
        FOREIGN KEY (recommendation_candidate_id)
        REFERENCES public.recommendation_candidate (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_group_recommendation_share_message
        CHECK (message IS NULL OR char_length(message) <= 2000),
    CONSTRAINT ck_group_recommendation_share_deleted_timestamp
        CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);

CREATE UNIQUE INDEX uq_group_recommendation_share_active
    ON public.group_recommendation_share (
        group_id,
        shared_by_user_id,
        recommendation_candidate_id
    )
    WHERE deleted_at IS NULL;

CREATE INDEX ix_group_recommendation_share_group_created_active
    ON public.group_recommendation_share (group_id, created_at DESC, id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_group_recommendation_share_candidate
    ON public.group_recommendation_share (recommendation_candidate_id);

COMMENT ON COLUMN public.recommendation_session.request_context IS
    'Immutable, versioned public request snapshot; never use it in place of relational query fields.';
COMMENT ON COLUMN public.recommendation_candidate.feature_snapshot IS
    'Bounded point-in-time model features only; excludes raw comments and unrelated private content.';
COMMENT ON COLUMN public.recommendation_candidate.evidence_snapshot IS
    'Verified point-in-time evidence used to validate and explain this candidate.';
COMMENT ON TABLE public.recommendation_feedback IS
    'Append-only explicit interaction events. Missing feedback is unknown, never an implicit rejection.';
