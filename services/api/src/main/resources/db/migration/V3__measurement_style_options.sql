-- Expand measurement profiles for kameez / shalwar / style options
ALTER TABLE measurement_profiles
    ALTER COLUMN fit_type DROP NOT NULL;

ALTER TABLE measurement_profiles
    ADD COLUMN IF NOT EXISTS collar_length NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS shalwar_bottom NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS back_style VARCHAR(32),
    ADD COLUMN IF NOT EXISTS sleeve_style VARCHAR(32),
    ADD COLUMN IF NOT EXISTS button_style VARCHAR(32),
    ADD COLUMN IF NOT EXISTS collar_style VARCHAR(32),
    ADD COLUMN IF NOT EXISTS cuff_style VARCHAR(32);
