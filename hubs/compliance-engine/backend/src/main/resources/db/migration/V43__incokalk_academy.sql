-- ============================================================
-- V43 — P4.27 Module formation / académie (IncoKalk Academy)
-- ============================================================

CREATE TABLE training_modules (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    difficulty VARCHAR(20),
    duration_minutes INT,
    content_html TEXT,
    video_url VARCHAR(500),
    quiz_data JSONB,
    passing_score INT NOT NULL DEFAULT 80,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    version INT NOT NULL DEFAULT 0,
    image_url VARCHAR(500),
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_training_modules_published ON training_modules(is_published);
CREATE INDEX idx_training_modules_category ON training_modules(category);
CREATE INDEX idx_training_modules_difficulty ON training_modules(difficulty);
CREATE INDEX idx_training_modules_created_by ON training_modules(created_by);

CREATE TABLE course_enrollments (
    id UUID PRIMARY KEY,
    module_id UUID NOT NULL REFERENCES training_modules(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    progress_percent INT NOT NULL DEFAULT 0,
    quiz_attempts INT NOT NULL DEFAULT 0,
    best_score INT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_course_enrollments_module ON course_enrollments(module_id);
CREATE INDEX idx_course_enrollments_company ON course_enrollments(company_id);
CREATE INDEX idx_course_enrollments_user ON course_enrollments(user_id);
CREATE INDEX idx_course_enrollments_status ON course_enrollments(status);
CREATE UNIQUE INDEX idx_course_enrollments_unique ON course_enrollments(company_id, user_id, module_id);

CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES course_enrollments(id) ON DELETE CASCADE,
    module_id UUID NOT NULL REFERENCES training_modules(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    answers JSONB,
    score INT NOT NULL,
    passed BOOLEAN NOT NULL,
    attempt_number INT NOT NULL,
    started_at TIMESTAMP,
    submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quiz_attempts_enrollment ON quiz_attempts(enrollment_id);
CREATE INDEX idx_quiz_attempts_module ON quiz_attempts(module_id);
CREATE INDEX idx_quiz_attempts_company ON quiz_attempts(company_id);
