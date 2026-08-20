-- Preserve legacy route values for existing assistant messages while allowing v2 messages to omit them.
ALTER TABLE public.chat_message
    DROP CONSTRAINT ck_chat_message_role_metadata;

ALTER TABLE public.chat_message
    ADD CONSTRAINT ck_chat_message_role_metadata CHECK (
        (
            role = 'USER'
            AND route IS NULL
            AND response_status IS NULL
            AND agent_trace_id IS NULL
        )
        OR (
            role = 'ASSISTANT'
            AND response_status IS NOT NULL
        )
    );
