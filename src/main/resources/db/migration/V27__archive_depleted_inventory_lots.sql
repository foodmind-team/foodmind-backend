-- Depleted lots have no usable inventory and should not remain visible as
-- duplicate active rows. Historical cooking/shopping references stay intact.
UPDATE public.inventory_lot
SET archived_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE archived_at IS NULL
  AND on_hand = 0
  AND reserved = 0;
