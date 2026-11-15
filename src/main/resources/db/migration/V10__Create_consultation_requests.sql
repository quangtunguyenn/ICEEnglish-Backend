-- Apply explicitly using the project's database deployment process.
CREATE TABLE consultation_requests (
    id uuid PRIMARY KEY,
    student_fullname varchar(150) NOT NULL,
    bod integer NOT NULL CHECK (bod >= 1),
    target_grade numeric NOT NULL CHECK (target_grade >= 0 AND target_grade <= 10),
    phone_number_request varchar(30) NOT NULL,
    date_request timestamp without time zone NOT NULL,
    status varchar(8) NOT NULL CHECK (status IN ('NOT_DONE', 'DONE'))
);

CREATE INDEX idx_consultation_requests_date ON consultation_requests (date_request DESC, id DESC);
CREATE INDEX idx_consultation_requests_status_date ON consultation_requests (status, date_request DESC, id DESC);
