CREATE TABLE public.cooking_plan_inventory_consumption (
    id uuid NOT NULL PRIMARY KEY,
    plan_id uuid NOT NULL REFERENCES public.cooking_plan(id) ON DELETE RESTRICT,
    allocation_id uuid NOT NULL REFERENCES public.cooking_plan_lot_allocation(id) ON DELETE RESTRICT,
    quantity numeric(12,3) NOT NULL CHECK (quantity > 0),
    consumed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cooking_plan_inventory_consumption_allocation UNIQUE (allocation_id)
);
