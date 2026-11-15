DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'sbd_mock'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'password_mock'
        ) THEN
            UPDATE users SET password_mock = sbd_mock WHERE password_mock IS NULL;
        ELSE
            ALTER TABLE users RENAME COLUMN sbd_mock TO password_mock;
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'bod'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'dob'
        ) THEN
            UPDATE users SET dob = bod WHERE dob IS NULL;
        ELSE
            ALTER TABLE users RENAME COLUMN bod TO dob;
        END IF;
    END IF;
END
$$;
