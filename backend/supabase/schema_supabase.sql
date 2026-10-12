-- KONEKTED database for SUPABASE (PostgreSQL).
-- Paste this whole file into Supabase > SQL Editor > New query, then press Run. Run it ONCE.
--
-- It is the same as backend/schema.sql (without the psql-only "\c" line) plus a lock at the end.
-- If you change schema.sql later, make the same change here (or ask for this file to be rebuilt).

-- Create database (run as superuser; skip if already created)
-- CREATE DATABASE classroom_sync;


-- Users table
-- password_hash is nullable: NULL means this row is only an identity cache
-- (created by get_or_create_user() when some other sync flow - e.g. a
-- student finding a class - first saw this name/email/role), not a real
-- registered account. It's only set once someone actually signs up via
-- register.php, which is what makes account recovery (login.php, after a
-- reinstall) possible for that row.
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(190) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('Teacher', 'Student')),
    password_hash VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_users_email UNIQUE (email)
);

-- Safe to re-run on a database created before this column existed.
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Stored file name (in the attachments folder) of the account's profile
-- picture, set by upload_profile_picture.php. NULL = no picture.
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_picture VARCHAR(255);

-- Classes table
CREATE TABLE IF NOT EXISTS classes (
    id SERIAL PRIMARY KEY,
    class_name VARCHAR(150) NOT NULL,
    subject VARCHAR(150) NOT NULL,
    section VARCHAR(100) NOT NULL,
    school_year VARCHAR(50) NOT NULL,
    class_code VARCHAR(20) NOT NULL,
    teacher_id INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_classes_code UNIQUE (class_code),
    CONSTRAINT fk_classes_teacher FOREIGN KEY (teacher_id) REFERENCES users(id)
);

-- Auto-update updated_at on classes
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_classes_updated ON classes;
CREATE TRIGGER trg_classes_updated
    BEFORE UPDATE ON classes
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Class members table
CREATE TABLE IF NOT EXISTS class_members (
    id SERIAL PRIMARY KEY,
    class_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_class_student UNIQUE (class_id, student_id),
    CONSTRAINT fk_members_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_members_student FOREIGN KEY (student_id) REFERENCES users(id)
);

-- Lessons table
CREATE TABLE IF NOT EXISTS lessons (
    id SERIAL PRIMARY KEY,
    class_id INTEGER NOT NULL,
    remote_id INTEGER,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    subject VARCHAR(150) NOT NULL,
    video_link VARCHAR(500),
    image_path VARCHAR(500),
    image_name VARCHAR(255),
    attachment_path VARCHAR(500),
    attachment_name VARCHAR(255),
    attachment_mime VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lessons_class FOREIGN KEY (class_id) REFERENCES classes(id)
);

-- Auto-update updated_at on lessons
DROP TRIGGER IF EXISTS trg_lessons_updated ON lessons;
CREATE TRIGGER trg_lessons_updated
    BEFORE UPDATE ON lessons
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Lesson attachments table: a lesson can have any number of these, of any
-- mix of types (added one at a time from the app's "+ Add Attachment" flow).
-- Replaces the old single image_path/attachment_path/video_link columns on
-- lessons above, which are left in place unused rather than dropped (avoids
-- a disruptive column-drop migration on both the Android and server sides
-- for a handful of now-unused columns).
CREATE TABLE IF NOT EXISTS lesson_attachments (
    id SERIAL PRIMARY KEY,
    lesson_id INTEGER NOT NULL,
    type VARCHAR(20) NOT NULL, -- 'IMAGE' | 'FILE' | 'VIDEO_LINK'
    file_path VARCHAR(500),    -- server-stored relative path (IMAGE/FILE only)
    name VARCHAR(255),         -- display name, or the raw URL for VIDEO_LINK
    mime_type VARCHAR(100),
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lesson_attachments_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_lesson_attachments_lesson_id ON lesson_attachments (lesson_id);

-- Quizzes table
CREATE TABLE IF NOT EXISTS quizzes (
    id SERIAL PRIMARY KEY,
    lesson_id INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    instructions TEXT NOT NULL DEFAULT '',
    time_limit_minutes INTEGER,
    passing_score INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'Draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quizzes_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id)
);

DROP TRIGGER IF EXISTS trg_quizzes_updated ON quizzes;
CREATE TRIGGER trg_quizzes_updated
    BEFORE UPDATE ON quizzes
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Questions table (supports MULTIPLE_CHOICE, IDENTIFICATION, ESSAY)
CREATE TABLE IF NOT EXISTS questions (
    id SERIAL PRIMARY KEY,
    quiz_id INTEGER NOT NULL,
    question TEXT NOT NULL,
    question_type VARCHAR(20) NOT NULL DEFAULT 'MULTIPLE_CHOICE'
        CHECK (question_type IN ('MULTIPLE_CHOICE', 'IDENTIFICATION', 'ESSAY')),
    choice_a VARCHAR(500) DEFAULT '',
    choice_b VARCHAR(500) DEFAULT '',
    choice_c VARCHAR(500) DEFAULT '',
    choice_d VARCHAR(500) DEFAULT '',
    correct_answer VARCHAR(1) DEFAULT '' CHECK (correct_answer IN ('A', 'B', 'C', 'D', '')),
    answer_text TEXT,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_questions_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes(id)
);

-- Quiz results table
CREATE TABLE IF NOT EXISTS quiz_results (
    id SERIAL PRIMARY KEY,
    quiz_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    score INTEGER NOT NULL DEFAULT 0,
    correct_count INTEGER NOT NULL DEFAULT 0,
    total_questions INTEGER NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_quiz_student_result UNIQUE (quiz_id, student_id),
    CONSTRAINT fk_results_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes(id),
    CONSTRAINT fk_results_student FOREIGN KEY (student_id) REFERENCES users(id)
);

-- Attendance table
CREATE TABLE IF NOT EXISTS attendance (
    id SERIAL PRIMARY KEY,
    class_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Present' CHECK (status IN ('Present', 'Absent', 'Late', 'Excused')),
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_class_student_date UNIQUE (class_id, student_id, date),
    CONSTRAINT fk_attendance_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES users(id)
);

DROP TRIGGER IF EXISTS trg_attendance_updated ON attendance;
CREATE TRIGGER trg_attendance_updated
    BEFORE UPDATE ON attendance
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Join requests table (student requests to join a class, teacher approves/denies)
CREATE TABLE IF NOT EXISTS join_requests (
    id SERIAL PRIMARY KEY,
    class_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'DENIED')),
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT uniq_join_req_class_student UNIQUE (class_id, student_id),
    CONSTRAINT fk_join_requests_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_join_requests_student FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_join_requests_class ON join_requests (class_id);
CREATE INDEX IF NOT EXISTS idx_join_requests_student ON join_requests (student_id);

-- Login protection (see "Login protection" in api/common.php).
-- One row per phone that is logged in: login.php / register.php create it and
-- hand the app the secret token; only the token's SHA-256 is stored here.
-- Rows are deleted by logout.php, expire after 90 days unused, and go away with
-- their user. (api/common.php's ensure_auth_schema() runs these same statements
-- on the first login, so a server updated without re-running this file works too.)
CREATE TABLE IF NOT EXISTS auth_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_user ON auth_tokens (user_id);

-- Recent login / sign-up attempts, so passwords can't be guessed by trying
-- them one after another (8 failures in 15 minutes lock that email for a
-- while). Old rows are deleted automatically.
CREATE TABLE IF NOT EXISTS login_attempts (
    id BIGSERIAL PRIMARY KEY,
    kind VARCHAR(10) NOT NULL,
    email VARCHAR(190),
    ip VARCHAR(64),
    succeeded BOOLEAN NOT NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_login_attempts_email ON login_attempts (kind, email, attempted_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip ON login_attempts (kind, ip, attempted_at);

-- ---------------------------------------------------------------------------
-- LOCK. Supabase also publishes the tables of the "public" area on the internet through its
-- own API. KONEKTED never uses that API (the app talks to the PHP server, and the PHP server
-- talks to the database directly as the owner), so every table is locked for the API roles.
-- Without this, anyone who finds the project's public key could read the users table
-- (names, emails, password hashes). The PHP server is not affected: the owner role ignores
-- row security.
-- ---------------------------------------------------------------------------
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE class_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE lessons ENABLE ROW LEVEL SECURITY;
ALTER TABLE lesson_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE quizzes ENABLE ROW LEVEL SECURITY;
ALTER TABLE questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE quiz_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE join_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE auth_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE login_attempts ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon, authenticated;
