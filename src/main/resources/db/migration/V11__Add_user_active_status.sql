-- Apply before deploying code that reads users.is_active.
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active boolean;
UPDATE users SET is_active = true WHERE is_active IS NULL;
ALTER TABLE users ALTER COLUMN is_active SET DEFAULT true;
ALTER TABLE users ALTER COLUMN is_active SET NOT NULL;
