ALTER TABLE users
    ADD COLUMN IF NOT EXISTS student_irn varchar(50),
    ADD COLUMN IF NOT EXISTS password_mock varchar(100),
    ADD COLUMN IF NOT EXISTS dob date,
    ADD COLUMN IF NOT EXISTS gender varchar(20);
