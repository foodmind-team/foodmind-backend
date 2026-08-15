-- Stable recommendation exclusions and conservative catalogue evidence metadata.

ALTER TABLE public.meal
    ADD COLUMN allergen_evidence_complete boolean NOT NULL DEFAULT false;

-- V11 is the only reviewed catalogue seed that predates this evidence flag.
UPDATE public.meal
SET allergen_evidence_complete = true
WHERE id IN (
    '20000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000002',
    '20000000-0000-4000-8000-000000000003',
    '20000000-0000-4000-8000-000000000004',
    '20000000-0000-4000-8000-000000000005',
    '20000000-0000-4000-8000-000000000006',
    '20000000-0000-4000-8000-000000000007',
    '20000000-0000-4000-8000-000000000008'
);

ALTER TABLE public.place_meal
    ADD COLUMN recommendation_category_code varchar(32),
    ADD CONSTRAINT ck_place_meal_recommendation_category_code
        CHECK (
            recommendation_category_code IS NULL
            OR recommendation_category_code ~ '^[A-Z][A-Z0-9_]{0,31}$'
        );

CREATE INDEX ix_recommendation_feedback_user_rejection
    ON public.recommendation_feedback (
        user_id,
        event_type,
        reason_code,
        effective_until,
        candidate_id
    )
    WHERE event_type = 'REJECTED';

COMMENT ON COLUMN public.meal.allergen_evidence_complete IS
    'True only when an empty allergen mapping was explicitly reviewed; false means fail closed for allergen-constrained recommendations.';

COMMENT ON COLUMN public.place_meal.recommendation_category_code IS
    'Optional bounded category used for recommendation diversity; not a dietary or safety claim.';
