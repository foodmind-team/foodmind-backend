ALTER TABLE user_preference
    ADD COLUMN cooking_region varchar(2) NOT NULL DEFAULT 'SG',
    ADD CONSTRAINT ck_user_preference_cooking_region
        CHECK (cooking_region IN ('SG', 'US', 'CN'));

COMMENT ON COLUMN user_preference.cooking_region IS
    'Account-synchronised region used for cooking guidance across Web and Android.';
