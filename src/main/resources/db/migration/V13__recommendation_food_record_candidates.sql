-- Recommendation candidates may be a current catalogue offering or an authorised historical food record.
ALTER TABLE public.recommendation_candidate
    ADD COLUMN candidate_source_type varchar(20),
    ADD COLUMN food_record_id uuid;

UPDATE public.recommendation_candidate
SET candidate_source_type = 'PLACE_MEAL'
WHERE candidate_source_type IS NULL;

ALTER TABLE public.recommendation_candidate
    ALTER COLUMN candidate_source_type SET NOT NULL,
    ALTER COLUMN place_meal_id DROP NOT NULL,
    ADD CONSTRAINT fk_recommendation_candidate_food_record
        FOREIGN KEY (food_record_id) REFERENCES public.food_record (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_recommendation_candidate_source_type
        CHECK (candidate_source_type IN ('PLACE_MEAL', 'FOOD_RECORD')),
    ADD CONSTRAINT ck_recommendation_candidate_source
        CHECK (
            (candidate_source_type = 'PLACE_MEAL' AND place_meal_id IS NOT NULL AND food_record_id IS NULL)
            OR
            (candidate_source_type = 'FOOD_RECORD' AND food_record_id IS NOT NULL AND place_meal_id IS NULL)
        );

ALTER TABLE public.recommendation_candidate
    DROP CONSTRAINT uq_recommendation_candidate_session_place_meal;

CREATE UNIQUE INDEX uq_recommendation_candidate_session_place_meal_source
    ON public.recommendation_candidate (session_id, place_meal_id)
    WHERE candidate_source_type = 'PLACE_MEAL';

CREATE UNIQUE INDEX uq_recommendation_candidate_session_food_record_source
    ON public.recommendation_candidate (session_id, food_record_id)
    WHERE candidate_source_type = 'FOOD_RECORD';

CREATE INDEX ix_recommendation_candidate_food_record_created
    ON public.recommendation_candidate (food_record_id, created_at DESC)
    WHERE food_record_id IS NOT NULL;
