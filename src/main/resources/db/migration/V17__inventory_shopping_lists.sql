-- Persisted inventory management, shopping-list recovery and cooking-plan lineage.
-- External Agent calls are intentionally outside these database structures and
-- application transactions.

ALTER TABLE public.inventory_lot
    ADD COLUMN archived_at timestamptz;

CREATE INDEX ix_inventory_lot_user_active_expiry
    ON public.inventory_lot (user_id, expiry_date NULLS LAST, id)
    WHERE archived_at IS NULL;

ALTER TABLE public.cooking_plan
    ADD COLUMN parent_plan_id uuid,
    ADD COLUMN root_plan_id uuid;

UPDATE public.cooking_plan
SET root_plan_id = id
WHERE root_plan_id IS NULL;

ALTER TABLE public.cooking_plan
    ALTER COLUMN root_plan_id SET NOT NULL,
    ADD CONSTRAINT fk_cooking_plan_parent
        FOREIGN KEY (parent_plan_id) REFERENCES public.cooking_plan (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cooking_plan_root
        FOREIGN KEY (root_plan_id) REFERENCES public.cooking_plan (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_cooking_plan_parent_not_self
        CHECK (parent_plan_id IS NULL OR parent_plan_id <> id);

CREATE INDEX ix_cooking_plan_parent
    ON public.cooking_plan (parent_plan_id)
    WHERE parent_plan_id IS NOT NULL;

CREATE INDEX ix_cooking_plan_root_created
    ON public.cooking_plan (root_plan_id, created_at);

CREATE TABLE public.shopping_list (
    id                    uuid         NOT NULL,
    user_id               uuid         NOT NULL,
    source_plan_id        uuid         NOT NULL,
    root_plan_id          uuid         NOT NULL,
    original_servings     integer      NOT NULL,
    continuation_plan_id  uuid,
    status                varchar(16)  NOT NULL DEFAULT 'OPEN',
    created_at            timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at          timestamptz,
    version               bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_shopping_list PRIMARY KEY (id),
    CONSTRAINT fk_shopping_list_user
        FOREIGN KEY (user_id) REFERENCES public.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_shopping_list_source_plan
        FOREIGN KEY (source_plan_id) REFERENCES public.cooking_plan (id) ON DELETE RESTRICT,
    CONSTRAINT fk_shopping_list_root_plan
        FOREIGN KEY (root_plan_id) REFERENCES public.cooking_plan (id) ON DELETE RESTRICT,
    CONSTRAINT fk_shopping_list_continuation_plan
        FOREIGN KEY (continuation_plan_id) REFERENCES public.cooking_plan (id) ON DELETE RESTRICT,
    CONSTRAINT uq_shopping_list_user_source UNIQUE (user_id, source_plan_id),
    CONSTRAINT ck_shopping_list_original_servings CHECK (original_servings BETWEEN 1 AND 24),
    CONSTRAINT ck_shopping_list_status CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT ck_shopping_list_completion CHECK (
        (status = 'OPEN' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_shopping_list_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_shopping_list_version CHECK (version >= 0)
);

CREATE INDEX ix_shopping_list_user_status_updated
    ON public.shopping_list (user_id, status, updated_at DESC, id DESC);

CREATE INDEX ix_shopping_list_source_plan
    ON public.shopping_list (source_plan_id);

CREATE INDEX ix_shopping_list_root_plan
    ON public.shopping_list (root_plan_id);

CREATE INDEX ix_shopping_list_continuation_plan
    ON public.shopping_list (continuation_plan_id)
    WHERE continuation_plan_id IS NOT NULL;

CREATE TRIGGER trg_shopping_list_set_updated_at
BEFORE UPDATE ON public.shopping_list
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();

CREATE TABLE public.shopping_list_item (
    id                  uuid          NOT NULL,
    shopping_list_id    uuid          NOT NULL,
    sequence_no         smallint      NOT NULL,
    ingredient_name     varchar(160)  NOT NULL,
    required_quantity   numeric(12,3) NOT NULL,
    purchased_quantity  numeric(12,3) NOT NULL,
    unit                varchar(16)   NOT NULL,
    expiry_date         date,
    checked             boolean       NOT NULL DEFAULT false,
    inventory_lot_id    uuid,
    created_at          timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT pk_shopping_list_item PRIMARY KEY (id),
    CONSTRAINT fk_shopping_list_item_list
        FOREIGN KEY (shopping_list_id) REFERENCES public.shopping_list (id) ON DELETE CASCADE,
    CONSTRAINT fk_shopping_list_item_inventory_lot
        FOREIGN KEY (inventory_lot_id) REFERENCES public.inventory_lot (id) ON DELETE RESTRICT,
    CONSTRAINT uq_shopping_list_item_sequence UNIQUE (shopping_list_id, sequence_no),
    CONSTRAINT ck_shopping_list_item_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_shopping_list_item_name CHECK (
        ingredient_name = btrim(ingredient_name) AND ingredient_name <> ''
    ),
    CONSTRAINT ck_shopping_list_item_required_quantity CHECK (
        required_quantity > 0 AND required_quantity < 'Infinity'::numeric
    ),
    CONSTRAINT ck_shopping_list_item_purchased_quantity CHECK (
        purchased_quantity > 0 AND purchased_quantity < 'Infinity'::numeric
    ),
    CONSTRAINT ck_shopping_list_item_unit CHECK (unit = btrim(unit) AND unit <> ''),
    CONSTRAINT ck_shopping_list_item_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_shopping_list_item_version CHECK (version >= 0)
);

CREATE INDEX ix_shopping_list_item_list
    ON public.shopping_list_item (shopping_list_id, sequence_no);

CREATE INDEX ix_shopping_list_item_inventory_lot
    ON public.shopping_list_item (inventory_lot_id)
    WHERE inventory_lot_id IS NOT NULL;

CREATE TRIGGER trg_shopping_list_item_set_updated_at
BEFORE UPDATE ON public.shopping_list_item
FOR EACH ROW EXECUTE FUNCTION public.foodmind_set_updated_at();
