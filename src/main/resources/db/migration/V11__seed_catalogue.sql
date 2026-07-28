-- Deterministic, reviewed MVP catalogue data.
--
-- All identifiers and timestamps are fixed so shared fixtures are stable
-- across developer, CI, and demo databases. Every ON CONFLICT clause names
-- the stable unique key that makes an exact replay harmless without masking a
-- collision on a different constraint or overwriting later maintenance.
-- Place names and observations are synthetic demo content. Observations are
-- decision-support evidence, never inspections or safety certifications.

INSERT INTO cuisine (id, code, name, created_at, updated_at, version)
VALUES
    ('10000000-0000-4000-8000-000000000001', 'SINGAPOREAN', 'Singaporean', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('10000000-0000-4000-8000-000000000002', 'CHINESE', 'Chinese', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('10000000-0000-4000-8000-000000000003', 'MALAY', 'Malay', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('10000000-0000-4000-8000-000000000004', 'INDIAN', 'Indian', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('10000000-0000-4000-8000-000000000005', 'JAPANESE', 'Japanese', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO dietary_tag (id, code, name, created_at, updated_at, version)
VALUES
    ('11000000-0000-4000-8000-000000000001', 'VEGETARIAN', 'Vegetarian', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('11000000-0000-4000-8000-000000000002', 'VEGAN', 'Vegan', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO allergen (id, code, name, created_at, updated_at, version)
VALUES
    ('12000000-0000-4000-8000-000000000001', 'PEANUT', 'Peanut', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('12000000-0000-4000-8000-000000000002', 'TREE_NUT', 'Tree Nut', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('12000000-0000-4000-8000-000000000003', 'MILK', 'Milk', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('12000000-0000-4000-8000-000000000004', 'EGG', 'Egg', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('12000000-0000-4000-8000-000000000005', 'GLUTEN', 'Gluten', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('12000000-0000-4000-8000-000000000006', 'SOY', 'Soy', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('12000000-0000-4000-8000-000000000007', 'FISH', 'Fish', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('12000000-0000-4000-8000-000000000008', 'SHELLFISH', 'Shellfish', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('12000000-0000-4000-8000-000000000009', 'SESAME', 'Sesame', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO meal (
    id, name, description, cuisine_id, meal_type, default_spice_level,
    curation_status, created_at, updated_at, version
)
VALUES
    (
        '20000000-0000-4000-8000-000000000001',
        'Hainanese Chicken Rice',
        'Poached chicken with fragrant rice, cucumber, and a light soy dressing.',
        '10000000-0000-4000-8000-000000000001',
        'LUNCH',
        0,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '20000000-0000-4000-8000-000000000002',
        'Wok-Seared Vegetable Fried Rice',
        'Egg-free fried rice with mixed vegetables and a soy-based seasoning.',
        '10000000-0000-4000-8000-000000000002',
        'DINNER',
        1,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '20000000-0000-4000-8000-000000000003',
        'Chana Masala with Rice',
        'Chickpeas simmered with tomato and warm spices, served with rice.',
        '10000000-0000-4000-8000-000000000004',
        'DINNER',
        3,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '20000000-0000-4000-8000-000000000004',
        'Vegetable Tofu Donburi',
        'Rice bowl with tofu, seasonal vegetables, ginger, soy, and sesame.',
        '10000000-0000-4000-8000-000000000005',
        'LUNCH',
        1,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '20000000-0000-4000-8000-000000000005',
        'Nasi Lemak with Egg',
        'Coconut rice with egg, anchovies, peanuts, cucumber, and sambal.',
        '10000000-0000-4000-8000-000000000003',
        'BREAKFAST',
        2,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '20000000-0000-4000-8000-000000000006',
        'Fishball Noodle Soup',
        'Noodles and fishballs in a light broth with leafy greens.',
        '10000000-0000-4000-8000-000000000001',
        'LUNCH',
        1,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '20000000-0000-4000-8000-000000000007',
        'Grilled Salmon Donburi',
        'Grilled salmon over rice with vegetables, soy glaze, and sesame.',
        '10000000-0000-4000-8000-000000000005',
        'DINNER',
        1,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '20000000-0000-4000-8000-000000000008',
        'Vegetable Bee Hoon',
        'Rice vermicelli stir-fried with cabbage, carrot, and a light soy seasoning.',
        '10000000-0000-4000-8000-000000000001',
        'BREAKFAST',
        1,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO meal_dietary_tag (meal_id, dietary_tag_id)
VALUES
    ('20000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000001'),
    ('20000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000002'),
    ('20000000-0000-4000-8000-000000000003', '11000000-0000-4000-8000-000000000001'),
    ('20000000-0000-4000-8000-000000000003', '11000000-0000-4000-8000-000000000002'),
    ('20000000-0000-4000-8000-000000000004', '11000000-0000-4000-8000-000000000001'),
    ('20000000-0000-4000-8000-000000000004', '11000000-0000-4000-8000-000000000002'),
    ('20000000-0000-4000-8000-000000000008', '11000000-0000-4000-8000-000000000001'),
    ('20000000-0000-4000-8000-000000000008', '11000000-0000-4000-8000-000000000002')
ON CONFLICT (meal_id, dietary_tag_id) DO NOTHING;

INSERT INTO meal_allergen (meal_id, allergen_id)
VALUES
    ('20000000-0000-4000-8000-000000000001', '12000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000001', '12000000-0000-4000-8000-000000000006'),
    ('20000000-0000-4000-8000-000000000002', '12000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000002', '12000000-0000-4000-8000-000000000006'),
    ('20000000-0000-4000-8000-000000000004', '12000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000004', '12000000-0000-4000-8000-000000000006'),
    ('20000000-0000-4000-8000-000000000004', '12000000-0000-4000-8000-000000000009'),
    ('20000000-0000-4000-8000-000000000005', '12000000-0000-4000-8000-000000000001'),
    ('20000000-0000-4000-8000-000000000005', '12000000-0000-4000-8000-000000000004'),
    ('20000000-0000-4000-8000-000000000005', '12000000-0000-4000-8000-000000000007'),
    ('20000000-0000-4000-8000-000000000006', '12000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000006', '12000000-0000-4000-8000-000000000007'),
    ('20000000-0000-4000-8000-000000000007', '12000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000007', '12000000-0000-4000-8000-000000000006'),
    ('20000000-0000-4000-8000-000000000007', '12000000-0000-4000-8000-000000000007'),
    ('20000000-0000-4000-8000-000000000007', '12000000-0000-4000-8000-000000000009'),
    ('20000000-0000-4000-8000-000000000008', '12000000-0000-4000-8000-000000000006')
ON CONFLICT (meal_id, allergen_id) DO NOTHING;

INSERT INTO place (
    id, name, place_type, area, address_text, latitude, longitude, price_band,
    curation_status, created_at, updated_at, version
)
VALUES
    (
        '21000000-0000-4000-8000-000000000001',
        'Orchard Garden Kitchen',
        'CASUAL_DINING',
        'Orchard',
        'FoodMind synthetic demo listing, Orchard area',
        1.304800,
        103.831800,
        2,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '21000000-0000-4000-8000-000000000002',
        'Tiong Bahru Demo Hawker',
        'HAWKER_STALL',
        'Tiong Bahru',
        'FoodMind synthetic demo listing, Tiong Bahru area',
        1.285400,
        103.832000,
        1,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '21000000-0000-4000-8000-000000000003',
        'Serangoon Vegetarian Table',
        'CASUAL_DINING',
        'Serangoon',
        'FoodMind synthetic demo listing, Serangoon area',
        1.349600,
        103.873700,
        2,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    ),
    (
        '21000000-0000-4000-8000-000000000004',
        'Tampines Family Cafe',
        'CAFE',
        'Tampines',
        'FoodMind synthetic demo listing, Tampines area',
        1.352100,
        103.944800,
        2,
        'ACTIVE',
        '2026-07-28T00:00:00Z',
        '2026-07-28T00:00:00Z',
        0
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO place_meal (
    id, place_id, meal_id, display_name, price, currency, spice_level,
    available, availability_note, created_at, updated_at, version
)
VALUES
    ('22000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'Garden Chicken Rice', 7.50, 'SGD', 0, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000004', 'Ginger Tofu Donburi', 10.90, 'SGD', 1, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000003', '21000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000007', 'Sesame Salmon Rice Bowl', 15.90, 'SGD', 1, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000004', '21000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000005', 'Breakfast Nasi Lemak Set', 6.80, 'SGD', 2, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000005', '21000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000006', 'Clear Broth Fishball Noodles', 7.20, 'SGD', 1, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000006', '21000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000008', 'Morning Vegetable Bee Hoon', 5.80, 'SGD', 1, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000007', '21000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003', 'House Chana Masala Rice', 9.50, 'SGD', 3, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000008', '21000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000002', 'Garden Vegetable Fried Rice', 8.90, 'SGD', 1, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000009', '21000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000001', 'Cafe Chicken Rice Plate', 8.50, 'SGD', 0, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('22000000-0000-4000-8000-000000000010', '21000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000003', 'Weekday Chickpea Curry Rice', 8.80, 'SGD', 2, true, 'Synthetic demo availability; verify before travel.', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0)
ON CONFLICT (place_id, meal_id, display_name) DO NOTHING;

INSERT INTO place_observation (
    id, place_id, observation_type, score, note, source_kind, observed_at,
    created_by_user_id, created_at
)
VALUES
    ('24000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000001', 'CLEANLINESS', 0.86, 'Synthetic demo curation observation; not an inspection or safety certification.', 'CURATED_DEMO', '2026-07-21T04:00:00Z', NULL, '2026-07-28T00:00:00Z'),
    ('24000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000002', 'CLEANLINESS', 0.78, 'Synthetic demo curation observation; not an inspection or safety certification.', 'CURATED_DEMO', '2026-07-21T04:00:00Z', NULL, '2026-07-28T00:00:00Z'),
    ('24000000-0000-4000-8000-000000000003', '21000000-0000-4000-8000-000000000003', 'CLEANLINESS', 0.90, 'Synthetic demo curation observation; not an inspection or safety certification.', 'CURATED_DEMO', '2026-07-21T04:00:00Z', NULL, '2026-07-28T00:00:00Z'),
    ('24000000-0000-4000-8000-000000000004', '21000000-0000-4000-8000-000000000004', 'CLEANLINESS', 0.82, 'Synthetic demo curation observation; not an inspection or safety certification.', 'CURATED_DEMO', '2026-07-21T04:00:00Z', NULL, '2026-07-28T00:00:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO food_product (
    id, name, brand, description, price, currency, place_id, curation_status,
    created_at, updated_at, version
)
VALUES
    ('23000000-0000-4000-8000-000000000001', 'Unsweetened Soy Drink', 'FoodMind Demo', 'Shelf-stable unsweetened soy drink used as controlled demo catalogue data.', 2.40, 'SGD', NULL, 'ACTIVE', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('23000000-0000-4000-8000-000000000002', 'Wholegrain Oat Cup', 'FoodMind Demo', 'Plain wholegrain oat cup used as controlled demo catalogue data.', 3.20, 'SGD', NULL, 'ACTIVE', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('23000000-0000-4000-8000-000000000003', 'Roasted Peanut Snack', 'FoodMind Demo', 'Single-serve roasted peanuts used as controlled allergen test data.', 1.80, 'SGD', NULL, 'ACTIVE', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO food_product_dietary_tag (food_product_id, dietary_tag_id)
VALUES
    ('23000000-0000-4000-8000-000000000001', '11000000-0000-4000-8000-000000000001'),
    ('23000000-0000-4000-8000-000000000001', '11000000-0000-4000-8000-000000000002'),
    ('23000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000001'),
    ('23000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000002'),
    ('23000000-0000-4000-8000-000000000003', '11000000-0000-4000-8000-000000000001'),
    ('23000000-0000-4000-8000-000000000003', '11000000-0000-4000-8000-000000000002')
ON CONFLICT (food_product_id, dietary_tag_id) DO NOTHING;

INSERT INTO food_product_allergen (food_product_id, allergen_id)
VALUES
    ('23000000-0000-4000-8000-000000000001', '12000000-0000-4000-8000-000000000006'),
    ('23000000-0000-4000-8000-000000000002', '12000000-0000-4000-8000-000000000005'),
    ('23000000-0000-4000-8000-000000000003', '12000000-0000-4000-8000-000000000001')
ON CONFLICT (food_product_id, allergen_id) DO NOTHING;

INSERT INTO recipe (
    id, name, description, cuisine_id, default_servings, prep_minutes,
    cook_minutes, estimated_cost, currency, curation_status, created_at,
    updated_at, version
)
VALUES
    ('30000000-0000-4000-8000-000000000001', 'Ginger Tofu Rice Bowl', 'A controlled vegan rice-bowl recipe with tofu, vegetables, ginger, soy, and sesame.', '10000000-0000-4000-8000-000000000005', 2, 15, 20, 9.50, 'SGD', 'ACTIVE', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('30000000-0000-4000-8000-000000000002', 'One-Pot Chana Masala', 'A controlled vegan chickpea and tomato curry recipe.', '10000000-0000-4000-8000-000000000004', 4, 15, 35, 12.00, 'SGD', 'ACTIVE', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('30000000-0000-4000-8000-000000000003', 'Weeknight Vegetable Bee Hoon', 'A controlled vegan rice-vermicelli recipe with cabbage and carrot.', '10000000-0000-4000-8000-000000000001', 3, 20, 15, 8.00, 'SGD', 'ACTIVE', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ingredient (
    id, canonical_name, default_unit, created_at, updated_at, version
)
VALUES
    ('31000000-0000-4000-8000-000000000001', 'Firm tofu', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000002', 'Jasmine rice', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000003', 'Fresh ginger', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000004', 'Soy sauce', 'ml', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000005', 'Sesame oil', 'ml', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000006', 'Chickpeas', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000007', 'Canned tomatoes', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000008', 'Brown onion', 'item', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000009', 'Garlic', 'clove', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000010', 'Garam masala', 'tsp', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000011', 'Vegetable stock', 'ml', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000012', 'Rice vermicelli', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000013', 'Carrot', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000014', 'Green cabbage', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000015', 'Neutral cooking oil', 'ml', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0),
    ('31000000-0000-4000-8000-000000000016', 'Broccoli', 'g', '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO recipe_ingredient (
    recipe_id, ingredient_id, quantity, unit, optional, sequence_no
)
VALUES
    ('30000000-0000-4000-8000-000000000001', '31000000-0000-4000-8000-000000000001', 300, 'g', false, 1),
    ('30000000-0000-4000-8000-000000000001', '31000000-0000-4000-8000-000000000002', 180, 'g', false, 2),
    ('30000000-0000-4000-8000-000000000001', '31000000-0000-4000-8000-000000000016', 180, 'g', false, 3),
    ('30000000-0000-4000-8000-000000000001', '31000000-0000-4000-8000-000000000003', 15, 'g', false, 4),
    ('30000000-0000-4000-8000-000000000001', '31000000-0000-4000-8000-000000000004', 30, 'ml', false, 5),
    ('30000000-0000-4000-8000-000000000001', '31000000-0000-4000-8000-000000000005', 10, 'ml', false, 6),
    ('30000000-0000-4000-8000-000000000002', '31000000-0000-4000-8000-000000000006', 480, 'g', false, 1),
    ('30000000-0000-4000-8000-000000000002', '31000000-0000-4000-8000-000000000007', 400, 'g', false, 2),
    ('30000000-0000-4000-8000-000000000002', '31000000-0000-4000-8000-000000000008', 1, 'item', false, 3),
    ('30000000-0000-4000-8000-000000000002', '31000000-0000-4000-8000-000000000009', 3, 'clove', false, 4),
    ('30000000-0000-4000-8000-000000000002', '31000000-0000-4000-8000-000000000003', 20, 'g', false, 5),
    ('30000000-0000-4000-8000-000000000002', '31000000-0000-4000-8000-000000000010', 2, 'tsp', false, 6),
    ('30000000-0000-4000-8000-000000000002', '31000000-0000-4000-8000-000000000011', 250, 'ml', false, 7),
    ('30000000-0000-4000-8000-000000000002', '31000000-0000-4000-8000-000000000015', 15, 'ml', false, 8),
    ('30000000-0000-4000-8000-000000000003', '31000000-0000-4000-8000-000000000012', 300, 'g', false, 1),
    ('30000000-0000-4000-8000-000000000003', '31000000-0000-4000-8000-000000000014', 200, 'g', false, 2),
    ('30000000-0000-4000-8000-000000000003', '31000000-0000-4000-8000-000000000013', 120, 'g', false, 3),
    ('30000000-0000-4000-8000-000000000003', '31000000-0000-4000-8000-000000000009', 2, 'clove', false, 4),
    ('30000000-0000-4000-8000-000000000003', '31000000-0000-4000-8000-000000000004', 25, 'ml', false, 5),
    ('30000000-0000-4000-8000-000000000003', '31000000-0000-4000-8000-000000000005', 8, 'ml', true, 6),
    ('30000000-0000-4000-8000-000000000003', '31000000-0000-4000-8000-000000000015', 20, 'ml', false, 7)
ON CONFLICT (recipe_id, sequence_no) DO NOTHING;

INSERT INTO recipe_step (recipe_id, step_no, instruction)
VALUES
    ('30000000-0000-4000-8000-000000000001', 1, 'Cook the jasmine rice according to its package directions.'),
    ('30000000-0000-4000-8000-000000000001', 2, 'Pat the tofu dry, cut it into cubes, and brown it in a pan.'),
    ('30000000-0000-4000-8000-000000000001', 3, 'Add the broccoli and ginger; cook until the broccoli is tender-crisp.'),
    ('30000000-0000-4000-8000-000000000001', 4, 'Stir in the soy sauce and sesame oil, then serve the tofu and vegetables over rice.'),
    ('30000000-0000-4000-8000-000000000002', 1, 'Warm the cooking oil in a pot and soften the chopped onion.'),
    ('30000000-0000-4000-8000-000000000002', 2, 'Add the garlic, ginger, and garam masala; stir for one minute.'),
    ('30000000-0000-4000-8000-000000000002', 3, 'Add the tomatoes, chickpeas, and vegetable stock.'),
    ('30000000-0000-4000-8000-000000000002', 4, 'Simmer uncovered until the sauce thickens, stirring occasionally.'),
    ('30000000-0000-4000-8000-000000000003', 1, 'Soak the rice vermicelli according to its package directions, then drain.'),
    ('30000000-0000-4000-8000-000000000003', 2, 'Warm the cooking oil and briefly cook the garlic.'),
    ('30000000-0000-4000-8000-000000000003', 3, 'Add the cabbage and carrot; stir-fry until just tender.'),
    ('30000000-0000-4000-8000-000000000003', 4, 'Add the vermicelli and soy sauce; toss until hot and evenly combined.'),
    ('30000000-0000-4000-8000-000000000003', 5, 'Finish with the optional sesame oil and serve immediately.')
ON CONFLICT (recipe_id, step_no) DO NOTHING;

INSERT INTO recipe_dietary_tag (recipe_id, dietary_tag_id)
VALUES
    ('30000000-0000-4000-8000-000000000001', '11000000-0000-4000-8000-000000000001'),
    ('30000000-0000-4000-8000-000000000001', '11000000-0000-4000-8000-000000000002'),
    ('30000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000001'),
    ('30000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000002'),
    ('30000000-0000-4000-8000-000000000003', '11000000-0000-4000-8000-000000000001'),
    ('30000000-0000-4000-8000-000000000003', '11000000-0000-4000-8000-000000000002')
ON CONFLICT (recipe_id, dietary_tag_id) DO NOTHING;

INSERT INTO recipe_allergen (recipe_id, allergen_id)
VALUES
    ('30000000-0000-4000-8000-000000000001', '12000000-0000-4000-8000-000000000005'),
    ('30000000-0000-4000-8000-000000000001', '12000000-0000-4000-8000-000000000006'),
    ('30000000-0000-4000-8000-000000000001', '12000000-0000-4000-8000-000000000009'),
    ('30000000-0000-4000-8000-000000000003', '12000000-0000-4000-8000-000000000005'),
    ('30000000-0000-4000-8000-000000000003', '12000000-0000-4000-8000-000000000006'),
    ('30000000-0000-4000-8000-000000000003', '12000000-0000-4000-8000-000000000009')
ON CONFLICT (recipe_id, allergen_id) DO NOTHING;
