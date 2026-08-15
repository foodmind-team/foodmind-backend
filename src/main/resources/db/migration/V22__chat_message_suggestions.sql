ALTER TABLE public.chat_message
    ADD COLUMN suggested_questions jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN suggested_destinations jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE public.chat_message
    ADD CONSTRAINT ck_chat_message_suggested_questions CHECK (
        jsonb_typeof(suggested_questions) = 'array'
        AND jsonb_array_length(suggested_questions) <= 3
    ),
    ADD CONSTRAINT ck_chat_message_suggested_destinations CHECK (
        jsonb_typeof(suggested_destinations) = 'array'
        AND jsonb_array_length(suggested_destinations) <= 3
    );

COMMENT ON COLUMN public.chat_message.suggested_questions IS
    'Follow-up questions generated with an assistant response and replayed from history.';

COMMENT ON COLUMN public.chat_message.suggested_destinations IS
    'FoodMind destinations suggested with an assistant response and replayed from history.';
