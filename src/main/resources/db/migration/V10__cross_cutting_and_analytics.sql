-- Cross-cutting request safety, security audit evidence, and presentation-neutral
-- analytics projections. Views expose owner keys so every application query can
-- apply its authenticated-user predicate before aggregation is returned.

CREATE TABLE public.idempotency_record (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    operation varchar(80) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    state varchar(20) NOT NULL DEFAULT 'IN_PROGRESS',
    resource_id uuid,
    response_status smallint,
    response_body jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,

    CONSTRAINT fk_idempotency_record_user
        FOREIGN KEY (user_id)
        REFERENCES public.app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_idempotency_record_key
        UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT ck_idempotency_record_operation
        CHECK (operation ~ '^[A-Z][A-Z0-9_]{0,79}$'),
    CONSTRAINT ck_idempotency_record_key
        CHECK (
            idempotency_key = btrim(idempotency_key)
            AND idempotency_key <> ''
        ),
    CONSTRAINT ck_idempotency_record_request_hash
        CHECK (request_hash::text ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idempotency_record_state
        CHECK (state IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_idempotency_record_response_status
        CHECK (
            response_status IS NULL
            OR response_status BETWEEN 100 AND 599
        ),
    CONSTRAINT ck_idempotency_record_response_body
        CHECK (
            response_body IS NULL
            OR jsonb_typeof(response_body) IN ('object', 'array')
        ),
    CONSTRAINT ck_idempotency_record_state_response
        CHECK (
            (
                state = 'IN_PROGRESS'
                AND response_status IS NULL
                AND response_body IS NULL
            )
            OR
            (
                state IN ('COMPLETED', 'FAILED')
                AND response_status IS NOT NULL
            )
        ),
    CONSTRAINT ck_idempotency_record_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_idempotency_record_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_idempotency_record_version
        CHECK (version >= 0)
);

COMMENT ON TABLE public.idempotency_record IS
    'Per-user command deduplication; a repeated key is valid only for the same canonical request hash.';
COMMENT ON COLUMN public.idempotency_record.response_body IS
    'Optional cached public-safe JSON only; secrets and unrestricted upstream payloads are prohibited.';

CREATE INDEX ix_idempotency_record_expiry
    ON public.idempotency_record (expires_at);

CREATE INDEX ix_idempotency_record_in_progress
    ON public.idempotency_record (created_at)
    WHERE state = 'IN_PROGRESS';

CREATE INDEX ix_idempotency_record_resource
    ON public.idempotency_record (resource_id)
    WHERE resource_id IS NOT NULL;

CREATE TRIGGER trg_idempotency_record_set_updated_at
    BEFORE UPDATE ON public.idempotency_record
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_set_updated_at();


CREATE TABLE public.audit_event (
    id uuid PRIMARY KEY,
    actor_user_id uuid,
    action varchar(100) NOT NULL,
    resource_type varchar(80) NOT NULL,
    resource_id uuid,
    outcome varchar(20) NOT NULL,
    correlation_id uuid NOT NULL,
    safe_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_event_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES public.app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_audit_event_action
        CHECK (action ~ '^[A-Z][A-Z0-9_]{0,99}$'),
    CONSTRAINT ck_audit_event_resource_type
        CHECK (resource_type ~ '^[A-Z][A-Z0-9_]{0,79}$'),
    CONSTRAINT ck_audit_event_outcome
        CHECK (outcome IN ('SUCCEEDED', 'DENIED', 'FAILED')),
    CONSTRAINT ck_audit_event_safe_metadata
        CHECK (jsonb_typeof(safe_metadata) = 'object')
);

COMMENT ON TABLE public.audit_event IS
    'Append-only security and business-transition evidence containing identifiers and explicitly safe metadata only.';
COMMENT ON COLUMN public.audit_event.safe_metadata IS
    'Must not contain tokens, credentials, chat text, comments, prompts, or sensitive dietary details.';

CREATE INDEX ix_audit_event_actor_created
    ON public.audit_event (actor_user_id, created_at DESC)
    WHERE actor_user_id IS NOT NULL;

CREATE INDEX ix_audit_event_resource_created
    ON public.audit_event (resource_type, resource_id, created_at DESC);

CREATE INDEX ix_audit_event_correlation
    ON public.audit_event (correlation_id, created_at);

CREATE INDEX ix_audit_event_action_outcome_created
    ON public.audit_event (action, outcome, created_at DESC);

CREATE FUNCTION public.foodmind_reject_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $function$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '55000',
        MESSAGE = 'audit_event is append-only';
END;
$function$;

CREATE TRIGGER trg_audit_event_append_only
    BEFORE UPDATE OR DELETE ON public.audit_event
    FOR EACH ROW
    EXECUTE FUNCTION public.foodmind_reject_audit_mutation();


-- app_user.time_zone is retained as an IANA identifier. The application
-- validates it on writes; this defensive resolver prevents one legacy invalid
-- value from making an entire dashboard query fail. UTC is the explicit
-- fallback and is surfaced in every projection.
CREATE FUNCTION public.foodmind_resolve_time_zone(candidate_time_zone text)
RETURNS text
LANGUAGE sql
STABLE
PARALLEL SAFE
SET search_path = pg_catalog
AS $function$
    SELECT COALESCE(
        (
            SELECT timezone_name.name
            FROM pg_catalog.pg_timezone_names AS timezone_name
            WHERE timezone_name.name = candidate_time_zone
            LIMIT 1
        ),
        'UTC'
    );
$function$;

COMMENT ON FUNCTION public.foodmind_resolve_time_zone(text) IS
    'Returns a valid IANA PostgreSQL time-zone name, falling back explicitly to UTC.';


-- One safe, comment-free event stream is the base for count, rating, repeat,
-- and spending metrics. Local bucket columns never depend on the connection's
-- session time zone.
CREATE VIEW public.analytics_consumption_event_v1
WITH (security_invoker = true)
AS
SELECT
    food.owner_user_id AS user_id,
    'FOOD'::varchar(10) AS record_type,
    food.id AS record_id,
    CASE
        WHEN food.meal_id IS NOT NULL
            THEN 'MEAL:' || food.meal_id::text
        ELSE
            'FOOD_NAME:'
            || lower(regexp_replace(btrim(food.meal_name_snapshot), '\s+', ' ', 'g'))
    END AS item_key,
    food.meal_name_snapshot AS item_name,
    food.cuisine_id,
    food.occurred_at,
    zone.aggregation_time_zone,
    local_time.local_occurred_at,
    local_time.local_occurred_at::date AS local_date,
    date_trunc('week', local_time.local_occurred_at)::date AS local_week_start,
    date_trunc('month', local_time.local_occurred_at)::date AS local_month_start,
    food.price,
    food.currency,
    food.rating,
    food.would_eat_again AS would_again
FROM public.food_record AS food
JOIN public.app_user AS account
    ON account.id = food.owner_user_id
CROSS JOIN LATERAL (
    SELECT public.foodmind_resolve_time_zone(account.time_zone)
        AS aggregation_time_zone
) AS zone
CROSS JOIN LATERAL (
    SELECT timezone(zone.aggregation_time_zone, food.occurred_at)
        AS local_occurred_at
) AS local_time
WHERE food.deleted_at IS NULL

UNION ALL

SELECT
    drink.owner_user_id AS user_id,
    'DRINK'::varchar(10) AS record_type,
    drink.id AS record_id,
    'DRINK_NAME:'
        || lower(regexp_replace(btrim(drink.drink_name), '\s+', ' ', 'g'))
        AS item_key,
    drink.drink_name AS item_name,
    NULL::uuid AS cuisine_id,
    drink.occurred_at,
    zone.aggregation_time_zone,
    local_time.local_occurred_at,
    local_time.local_occurred_at::date AS local_date,
    date_trunc('week', local_time.local_occurred_at)::date AS local_week_start,
    date_trunc('month', local_time.local_occurred_at)::date AS local_month_start,
    drink.price,
    drink.currency,
    drink.rating,
    drink.would_buy_again AS would_again
FROM public.drink_record AS drink
JOIN public.app_user AS account
    ON account.id = drink.owner_user_id
CROSS JOIN LATERAL (
    SELECT public.foodmind_resolve_time_zone(account.time_zone)
        AS aggregation_time_zone
) AS zone
CROSS JOIN LATERAL (
    SELECT timezone(zone.aggregation_time_zone, drink.occurred_at)
        AS local_occurred_at
) AS local_time
WHERE drink.deleted_at IS NULL;

COMMENT ON VIEW public.analytics_consumption_event_v1 IS
    'Owner-scoped, non-deleted food/drink events with explicit IANA-local calendar buckets and no free-form comments.';


CREATE VIEW public.analytics_consumption_period_v1
WITH (security_invoker = true)
AS
SELECT
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    count(*) AS record_count,
    count(*) FILTER (WHERE event.record_type = 'FOOD') AS meal_count,
    count(*) FILTER (WHERE event.record_type = 'DRINK') AS drink_count,
    count(event.rating) AS rated_record_count,
    round(avg(event.rating), 2) AS mean_rating,
    count(*) FILTER (WHERE event.would_again IS NOT NULL)
        AS would_again_decision_count,
    count(*) FILTER (WHERE event.would_again IS TRUE)
        AS would_again_yes_count,
    count(*) FILTER (WHERE event.would_again IS FALSE)
        AS would_again_no_count,
    round(
        (count(*) FILTER (WHERE event.would_again IS TRUE))::numeric
        / NULLIF(
            count(*) FILTER (WHERE event.would_again IS NOT NULL),
            0
        ),
        4
    ) AS would_again_rate
FROM public.analytics_consumption_event_v1 AS event
CROSS JOIN (
    VALUES ('DAY'::varchar(5)), ('WEEK'::varchar(5)), ('MONTH'::varchar(5))
) AS grain (period_grain)
CROSS JOIN LATERAL (
    SELECT CASE grain.period_grain
        WHEN 'DAY' THEN event.local_date
        WHEN 'WEEK' THEN event.local_week_start
        WHEN 'MONTH' THEN event.local_month_start
    END AS period_start_local
) AS period
GROUP BY
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local;

COMMENT ON VIEW public.analytics_consumption_period_v1 IS
    'Food/drink counts, mean rating, and Would Again metrics by user-local day, ISO-style Monday week, or month.';


-- Currency is deliberately part of the grouping key. No implicit exchange
-- conversion or cross-currency total is produced.
CREATE VIEW public.analytics_spending_period_v1
WITH (security_invoker = true)
AS
SELECT
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    event.currency,
    count(*) AS priced_record_count,
    sum(event.price) AS total_spend,
    round(avg(event.price), 2) AS mean_spend
FROM public.analytics_consumption_event_v1 AS event
CROSS JOIN (
    VALUES ('DAY'::varchar(5)), ('WEEK'::varchar(5)), ('MONTH'::varchar(5))
) AS grain (period_grain)
CROSS JOIN LATERAL (
    SELECT CASE grain.period_grain
        WHEN 'DAY' THEN event.local_date
        WHEN 'WEEK' THEN event.local_week_start
        WHEN 'MONTH' THEN event.local_month_start
    END AS period_start_local
) AS period
WHERE event.price IS NOT NULL
  AND event.currency IS NOT NULL
GROUP BY
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    event.currency;

COMMENT ON VIEW public.analytics_spending_period_v1 IS
    'Spending totals by user-local period and original ISO currency; values are never combined across currencies.';


CREATE VIEW public.analytics_cuisine_period_v1
WITH (security_invoker = true)
AS
SELECT
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    cuisine.id AS cuisine_id,
    cuisine.code AS cuisine_code,
    cuisine.name AS cuisine_name,
    count(*) AS meal_count
FROM public.analytics_consumption_event_v1 AS event
JOIN public.cuisine
    ON cuisine.id = event.cuisine_id
CROSS JOIN (
    VALUES ('DAY'::varchar(5)), ('WEEK'::varchar(5)), ('MONTH'::varchar(5))
) AS grain (period_grain)
CROSS JOIN LATERAL (
    SELECT CASE grain.period_grain
        WHEN 'DAY' THEN event.local_date
        WHEN 'WEEK' THEN event.local_week_start
        WHEN 'MONTH' THEN event.local_month_start
    END AS period_start_local
) AS period
WHERE event.record_type = 'FOOD'
GROUP BY
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    cuisine.id,
    cuisine.code,
    cuisine.name;

COMMENT ON VIEW public.analytics_cuisine_period_v1 IS
    'Cuisine distribution for classified food records by explicit user-local period.';


CREATE VIEW public.analytics_repeat_period_v1
WITH (security_invoker = true)
AS
SELECT
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    event.record_type,
    event.item_key,
    min(event.item_name) AS item_name,
    count(*) AS occurrence_count,
    greatest(count(*) - 1, 0) AS repeat_count,
    round(
        greatest(count(*) - 1, 0)::numeric / NULLIF(count(*), 0),
        4
    ) AS repeat_frequency,
    min(event.occurred_at) AS first_occurred_at,
    max(event.occurred_at) AS last_occurred_at
FROM public.analytics_consumption_event_v1 AS event
CROSS JOIN (
    VALUES ('DAY'::varchar(5)), ('WEEK'::varchar(5)), ('MONTH'::varchar(5))
) AS grain (period_grain)
CROSS JOIN LATERAL (
    SELECT CASE grain.period_grain
        WHEN 'DAY' THEN event.local_date
        WHEN 'WEEK' THEN event.local_week_start
        WHEN 'MONTH' THEN event.local_month_start
    END AS period_start_local
) AS period
GROUP BY
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    event.record_type,
    event.item_key;

COMMENT ON VIEW public.analytics_repeat_period_v1 IS
    'Per-item repeat count and repeat-event share; the first occurrence is not counted as a repeat.';


CREATE INDEX ix_recommendation_feedback_user_created
    ON public.recommendation_feedback (user_id, created_at, event_type);

-- This projection intentionally labels only explicit ACCEPTED and REJECTED
-- events. All other events expose NULL explicit_label; passive non-selection
-- never becomes a negative label.
CREATE VIEW public.analytics_recommendation_event_v1
WITH (security_invoker = true)
AS
SELECT
    feedback.user_id,
    feedback.id AS feedback_id,
    feedback.session_id,
    feedback.candidate_id,
    feedback.event_type,
    CASE feedback.event_type
        WHEN 'ACCEPTED' THEN 1::smallint
        WHEN 'REJECTED' THEN 0::smallint
        ELSE NULL::smallint
    END AS explicit_label,
    feedback.reason_code,
    feedback.rating,
    feedback.boolean_value,
    feedback.resulting_food_record_id,
    feedback.created_at,
    zone.aggregation_time_zone,
    local_time.local_created_at,
    local_time.local_created_at::date AS local_date,
    date_trunc('week', local_time.local_created_at)::date AS local_week_start,
    date_trunc('month', local_time.local_created_at)::date AS local_month_start,
    candidate.rank AS candidate_rank,
    candidate.candidate_type,
    candidate.feature_schema_version,
    session.model_version,
    session.model_status,
    session.fallback_version,
    session.fallback_status
FROM public.recommendation_feedback AS feedback
JOIN public.recommendation_session AS session
    ON session.id = feedback.session_id
   AND session.user_id = feedback.user_id
JOIN public.app_user AS account
    ON account.id = feedback.user_id
LEFT JOIN public.recommendation_candidate AS candidate
    ON candidate.id = feedback.candidate_id
   AND candidate.session_id = feedback.session_id
CROSS JOIN LATERAL (
    SELECT public.foodmind_resolve_time_zone(account.time_zone)
        AS aggregation_time_zone
) AS zone
CROSS JOIN LATERAL (
    SELECT timezone(zone.aggregation_time_zone, feedback.created_at)
        AS local_created_at
) AS local_time;

COMMENT ON VIEW public.analytics_recommendation_event_v1 IS
    'Owner-scoped explicit recommendation interactions with local buckets and bounded model metadata; passive non-selection is unlabeled.';


CREATE VIEW public.analytics_recommendation_period_v1
WITH (security_invoker = true)
AS
SELECT
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    count(*) FILTER (WHERE event.event_type = 'ACCEPTED')
        AS accepted_count,
    count(*) FILTER (WHERE event.event_type = 'REJECTED')
        AS rejected_count,
    count(*) FILTER (WHERE event.explicit_label IS NOT NULL)
        AS explicit_decision_count,
    round(
        (count(*) FILTER (WHERE event.event_type = 'ACCEPTED'))::numeric
        / NULLIF(
            count(*) FILTER (WHERE event.explicit_label IS NOT NULL),
            0
        ),
        4
    ) AS acceptance_rate,
    round(
        (count(*) FILTER (WHERE event.event_type = 'REJECTED'))::numeric
        / NULLIF(
            count(*) FILTER (WHERE event.explicit_label IS NOT NULL),
            0
        ),
        4
    ) AS rejection_rate,
    count(*) FILTER (WHERE event.event_type = 'RERECOMMEND_REQUESTED')
        AS rerecommend_requested_count,
    count(event.rating) FILTER (WHERE event.event_type = 'LATER_RATED')
        AS later_rating_count,
    round(
        avg(event.rating) FILTER (WHERE event.event_type = 'LATER_RATED'),
        2
    ) AS mean_later_rating,
    count(*) FILTER (
        WHERE event.event_type = 'WOULD_EAT_AGAIN'
          AND event.boolean_value IS NOT NULL
    ) AS would_eat_again_decision_count,
    count(*) FILTER (
        WHERE event.event_type = 'WOULD_EAT_AGAIN'
          AND event.boolean_value IS TRUE
    ) AS would_eat_again_yes_count,
    round(
        (
            count(*) FILTER (
                WHERE event.event_type = 'WOULD_EAT_AGAIN'
                  AND event.boolean_value IS TRUE
            )
        )::numeric
        / NULLIF(
            count(*) FILTER (
                WHERE event.event_type = 'WOULD_EAT_AGAIN'
                  AND event.boolean_value IS NOT NULL
            ),
            0
        ),
        4
    ) AS would_eat_again_rate
FROM public.analytics_recommendation_event_v1 AS event
CROSS JOIN (
    VALUES ('DAY'::varchar(5)), ('WEEK'::varchar(5)), ('MONTH'::varchar(5))
) AS grain (period_grain)
CROSS JOIN LATERAL (
    SELECT CASE grain.period_grain
        WHEN 'DAY' THEN event.local_date
        WHEN 'WEEK' THEN event.local_week_start
        WHEN 'MONTH' THEN event.local_month_start
    END AS period_start_local
) AS period
GROUP BY
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local;

COMMENT ON VIEW public.analytics_recommendation_period_v1 IS
    'Explicit recommendation feedback counts and denominator-safe rates by user-local period.';


CREATE VIEW public.analytics_rejection_reason_v1
WITH (security_invoker = true)
AS
SELECT
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    COALESCE(event.reason_code, 'UNSPECIFIED') AS reason_code,
    count(*) AS rejection_count
FROM public.analytics_recommendation_event_v1 AS event
CROSS JOIN (
    VALUES ('DAY'::varchar(5)), ('WEEK'::varchar(5)), ('MONTH'::varchar(5))
) AS grain (period_grain)
CROSS JOIN LATERAL (
    SELECT CASE grain.period_grain
        WHEN 'DAY' THEN event.local_date
        WHEN 'WEEK' THEN event.local_week_start
        WHEN 'MONTH' THEN event.local_month_start
    END AS period_start_local
) AS period
WHERE event.event_type = 'REJECTED'
GROUP BY
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    COALESCE(event.reason_code, 'UNSPECIFIED');

COMMENT ON VIEW public.analytics_rejection_reason_v1 IS
    'Explicit rejection distribution; missing optional reasons are reported as UNSPECIFIED.';


CREATE VIEW public.analytics_candidate_type_selection_v1
WITH (security_invoker = true)
AS
SELECT
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    event.candidate_type,
    count(*) AS accepted_count
FROM public.analytics_recommendation_event_v1 AS event
CROSS JOIN (
    VALUES ('DAY'::varchar(5)), ('WEEK'::varchar(5)), ('MONTH'::varchar(5))
) AS grain (period_grain)
CROSS JOIN LATERAL (
    SELECT CASE grain.period_grain
        WHEN 'DAY' THEN event.local_date
        WHEN 'WEEK' THEN event.local_week_start
        WHEN 'MONTH' THEN event.local_month_start
    END AS period_start_local
) AS period
WHERE event.event_type = 'ACCEPTED'
  AND event.candidate_type IS NOT NULL
GROUP BY
    event.user_id,
    event.aggregation_time_zone,
    grain.period_grain,
    period.period_start_local,
    event.candidate_type;

COMMENT ON VIEW public.analytics_candidate_type_selection_v1 IS
    'Accepted recommendation count by PERSONAL, EXPLORATORY, or GROUP_INSPIRED candidate type.';


-- Spending is intentionally absent here because weekly totals can have several
-- currencies. Consumers obtain the WEEK rows from analytics_spending_period_v1
-- and retain one total per currency.
CREATE VIEW public.analytics_weekly_recap_v1
WITH (security_invoker = true)
AS
SELECT
    COALESCE(consumption.user_id, recommendation.user_id) AS user_id,
    COALESCE(
        consumption.aggregation_time_zone,
        recommendation.aggregation_time_zone
    ) AS aggregation_time_zone,
    COALESCE(
        consumption.period_start_local,
        recommendation.period_start_local
    ) AS week_start_local,
    COALESCE(consumption.meal_count, 0::bigint) AS meal_count,
    COALESCE(consumption.drink_count, 0::bigint) AS drink_count,
    consumption.mean_rating,
    COALESCE(consumption.would_again_decision_count, 0::bigint)
        AS record_would_again_decision_count,
    consumption.would_again_rate AS record_would_again_rate,
    COALESCE(recommendation.accepted_count, 0::bigint) AS accepted_count,
    COALESCE(recommendation.rejected_count, 0::bigint) AS rejected_count,
    recommendation.acceptance_rate,
    COALESCE(recommendation.rerecommend_requested_count, 0::bigint)
        AS rerecommend_requested_count,
    recommendation.mean_later_rating,
    recommendation.would_eat_again_rate
        AS recommendation_would_eat_again_rate
FROM (
    SELECT *
    FROM public.analytics_consumption_period_v1
    WHERE period_grain = 'WEEK'
) AS consumption
FULL OUTER JOIN (
    SELECT *
    FROM public.analytics_recommendation_period_v1
    WHERE period_grain = 'WEEK'
) AS recommendation
    ON recommendation.user_id = consumption.user_id
   AND recommendation.aggregation_time_zone = consumption.aggregation_time_zone
   AND recommendation.period_start_local = consumption.period_start_local;

COMMENT ON VIEW public.analytics_weekly_recap_v1 IS
    'One owner-local weekly recap row for behavioural and explicit recommendation metrics; spending remains currency-grouped separately.';


-- Restricted raw input to the Backend-controlled ML snapshot exporter. This
-- view is never granted to the ML runtime and is not itself a privacy-safe
-- dataset: the Backend export job must reject unknown feature-schema versions,
-- rebuild features through that version's exact key/type allow-list, replace
-- user_id with its secret-keyed HMAC modelling key, remove all direct IDs, and
-- attach dataset schema/commit/time-range/row-count/checksum metadata before
-- publishing.
CREATE VIEW public.ml_interaction_export_source_v1
WITH (security_invoker = true)
AS
SELECT
    decision.user_id,
    decision.id AS decision_event_id,
    decision.session_id,
    decision.candidate_id,
    candidate.place_meal_id AS raw_offering_id,
    offering.meal_id AS raw_meal_id,
    CASE decision.event_type
        WHEN 'ACCEPTED' THEN 1::smallint
        WHEN 'REJECTED' THEN 0::smallint
    END AS explicit_label,
    decision.created_at AS decision_created_at,
    candidate.rank AS candidate_rank,
    candidate.candidate_type,
    candidate.feature_schema_version,
    candidate.feature_snapshot AS raw_feature_snapshot,
    session.created_at AS session_created_at,
    session.model_version,
    session.model_status,
    session.fallback_version,
    session.fallback_status
FROM public.recommendation_feedback AS decision
JOIN public.recommendation_session AS session
    ON session.id = decision.session_id
   AND session.user_id = decision.user_id
JOIN public.recommendation_candidate AS candidate
    ON candidate.id = decision.candidate_id
   AND candidate.session_id = decision.session_id
JOIN public.place_meal AS offering
    ON offering.id = candidate.place_meal_id
WHERE decision.event_type IN ('ACCEPTED', 'REJECTED');

COMMENT ON VIEW public.ml_interaction_export_source_v1 IS
    'Backend-only raw decision source; direct IDs, raw meal/offering IDs, and raw_feature_snapshot remain sensitive. Use foodmind_ml_interaction_export_rows_v1 with an explicit decision window and observation cutoff.';

CREATE FUNCTION public.foodmind_ml_interaction_export_rows_v1(
    p_decision_from timestamptz,
    p_decision_to timestamptz,
    p_observed_through timestamptz
)
RETURNS TABLE (
    user_id uuid,
    decision_event_id uuid,
    session_id uuid,
    candidate_id uuid,
    raw_offering_id uuid,
    raw_meal_id uuid,
    explicit_label smallint,
    decision_created_at timestamptz,
    later_rating numeric(2,1),
    later_rating_created_at timestamptz,
    would_eat_again boolean,
    would_eat_again_created_at timestamptz,
    candidate_rank smallint,
    candidate_type varchar(24),
    feature_schema_version varchar(40),
    raw_feature_snapshot jsonb,
    session_created_at timestamptz,
    model_version varchar(80),
    model_status varchar(32),
    fallback_version varchar(40),
    fallback_status varchar(32)
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF p_decision_from IS NULL
       OR p_decision_to IS NULL
       OR p_observed_through IS NULL
       OR p_decision_from >= p_decision_to
       OR p_decision_to > p_observed_through THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'invalid ML decision window or observation cutoff';
    END IF;

    RETURN QUERY
    SELECT
        source.user_id,
        source.decision_event_id,
        source.session_id,
        source.candidate_id,
        source.raw_offering_id,
        source.raw_meal_id,
        source.explicit_label,
        source.decision_created_at,
        rating_signal.rating,
        rating_signal.created_at,
        would_signal.boolean_value,
        would_signal.created_at,
        source.candidate_rank,
        source.candidate_type,
        source.feature_schema_version,
        source.raw_feature_snapshot,
        source.session_created_at,
        source.model_version,
        source.model_status,
        source.fallback_version,
        source.fallback_status
    FROM public.ml_interaction_export_source_v1 AS source
    LEFT JOIN LATERAL (
        SELECT signal.rating, signal.created_at
        FROM public.recommendation_feedback AS signal
        WHERE signal.user_id = source.user_id
          AND signal.session_id = source.session_id
          AND signal.candidate_id = source.candidate_id
          AND signal.event_type = 'LATER_RATED'
          AND signal.created_at >= source.decision_created_at
          AND signal.created_at < p_observed_through
        ORDER BY signal.created_at DESC, signal.id DESC
        LIMIT 1
    ) AS rating_signal ON true
    LEFT JOIN LATERAL (
        SELECT signal.boolean_value, signal.created_at
        FROM public.recommendation_feedback AS signal
        WHERE signal.user_id = source.user_id
          AND signal.session_id = source.session_id
          AND signal.candidate_id = source.candidate_id
          AND signal.event_type = 'WOULD_EAT_AGAIN'
          AND signal.created_at >= source.decision_created_at
          AND signal.created_at < p_observed_through
        ORDER BY signal.created_at DESC, signal.id DESC
        LIMIT 1
    ) AS would_signal ON true
    WHERE source.decision_created_at >= p_decision_from
      AND source.decision_created_at < p_decision_to;
END;
$function$;

COMMENT ON FUNCTION public.foodmind_ml_interaction_export_rows_v1(
    timestamptz,
    timestamptz,
    timestamptz
) IS
    'Restricted reproducible ML export rows bounded by a half-open decision window and an exclusive later-signal observation cutoff.';
