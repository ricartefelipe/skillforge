CREATE TABLE IF NOT EXISTS course (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    CONSTRAINT course_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS course_name_unique ON course (lower(name));

CREATE TABLE IF NOT EXISTS module (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES course (id),
    name text NOT NULL,
    position integer NOT NULL,
    CONSTRAINT module_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE IF NOT EXISTS lesson (
    id uuid PRIMARY KEY,
    module_id uuid NOT NULL REFERENCES module (id),
    title text NOT NULL,
    position integer NOT NULL,
    kind text NOT NULL,
    markdown text,
    media bytea,
    media_type text,
    CONSTRAINT lesson_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT lesson_kind CHECK (kind IN ('markdown', 'pdf', 'ebook', 'video')),
    -- A lesson carries markdown, or a file, and never both.
    CONSTRAINT lesson_one_content CHECK (
        (markdown IS NOT NULL) = (kind = 'markdown')
        AND (media IS NULL) = (markdown IS NOT NULL)
        AND (media_type IS NULL) = (media IS NULL)
        AND (markdown IS NULL OR length(btrim(markdown)) > 0)
    )
);

ALTER TABLE lesson DROP CONSTRAINT IF EXISTS lesson_kind;
ALTER TABLE lesson ADD CONSTRAINT lesson_kind CHECK (kind IN ('markdown', 'pdf', 'ebook', 'video'));

CREATE TABLE IF NOT EXISTS cohort (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES course (id),
    name text NOT NULL,
    teacher text NOT NULL,
    CONSTRAINT cohort_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT cohort_teacher_not_blank CHECK (length(btrim(teacher)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS cohort_course_name_unique ON cohort (course_id, lower(name));

CREATE TABLE IF NOT EXISTS enrollment (
    cohort_id uuid NOT NULL REFERENCES cohort (id),
    student text NOT NULL,
    CONSTRAINT enrollment_student_not_blank CHECK (length(btrim(student)) > 0),
    PRIMARY KEY (cohort_id, student)
);

CREATE UNIQUE INDEX IF NOT EXISTS enrollment_student_ci ON enrollment (cohort_id, lower(student));

CREATE TABLE IF NOT EXISTS practice (
    id uuid PRIMARY KEY,
    cohort_id uuid NOT NULL REFERENCES cohort (id),
    title text NOT NULL,
    prompt text NOT NULL,
    CONSTRAINT practice_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT practice_prompt_not_blank CHECK (length(btrim(prompt)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS practice_cohort_title_unique ON practice (cohort_id, lower(title));

CREATE TABLE IF NOT EXISTS submission (
    practice_id uuid NOT NULL REFERENCES practice (id),
    student text NOT NULL,
    body text NOT NULL,
    feedback text,
    CONSTRAINT submission_student_not_blank CHECK (length(btrim(student)) > 0),
    CONSTRAINT submission_body_not_blank CHECK (length(btrim(body)) > 0),
    CONSTRAINT submission_feedback_not_blank CHECK (feedback IS NULL OR length(btrim(feedback)) > 0),
    PRIMARY KEY (practice_id, student)
);

ALTER TABLE submission ADD COLUMN IF NOT EXISTS grade smallint;

CREATE TABLE IF NOT EXISTS doubt (
    id uuid PRIMARY KEY,
    lesson_id uuid NOT NULL REFERENCES lesson (id),
    author text NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    CONSTRAINT doubt_author_not_blank CHECK (length(btrim(author)) > 0),
    CONSTRAINT doubt_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT doubt_body_not_blank CHECK (length(btrim(body)) > 0)
);

CREATE TABLE IF NOT EXISTS exam (
    id uuid PRIMARY KEY,
    cohort_id uuid NOT NULL REFERENCES cohort (id),
    title text NOT NULL,
    CONSTRAINT exam_title_not_blank CHECK (length(btrim(title)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS exam_cohort_title_unique ON exam (cohort_id, lower(title));

CREATE TABLE IF NOT EXISTS question (
    id uuid PRIMARY KEY,
    exam_id uuid NOT NULL REFERENCES exam (id),
    position integer NOT NULL,
    prompt text NOT NULL,
    CONSTRAINT question_prompt_not_blank CHECK (length(btrim(prompt)) > 0)
);

CREATE TABLE IF NOT EXISTS choice (
    id uuid PRIMARY KEY,
    question_id uuid NOT NULL REFERENCES question (id),
    label text NOT NULL,
    correct boolean NOT NULL,
    CONSTRAINT choice_label_not_blank CHECK (length(btrim(label)) > 0)
);

CREATE TABLE IF NOT EXISTS attempt (
    exam_id uuid NOT NULL REFERENCES exam (id),
    student text NOT NULL,
    grade smallint NOT NULL,
    PRIMARY KEY (exam_id, student),
    CONSTRAINT attempt_grade_range CHECK (grade BETWEEN 0 AND 10),
    CONSTRAINT attempt_student_not_blank CHECK (length(btrim(student)) > 0)
);

ALTER TABLE practice ADD COLUMN IF NOT EXISTS due_at timestamptz;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS due_at timestamptz;

CREATE TABLE IF NOT EXISTS criterion (
    id uuid PRIMARY KEY,
    practice_id uuid NOT NULL REFERENCES practice (id),
    name text NOT NULL,
    points smallint NOT NULL,
    CONSTRAINT criterion_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT criterion_points CHECK (points BETWEEN 1 AND 10)
);

CREATE TABLE IF NOT EXISTS criterion_mark (
    criterion_id uuid NOT NULL REFERENCES criterion (id),
    student text NOT NULL,
    points smallint NOT NULL,
    PRIMARY KEY (criterion_id, student),
    CONSTRAINT criterion_mark_points CHECK (points >= 0)
);

CREATE TABLE IF NOT EXISTS class_day (
    id uuid PRIMARY KEY,
    cohort_id uuid NOT NULL REFERENCES cohort (id),
    held_on date NOT NULL,
    CONSTRAINT class_day_unique UNIQUE (cohort_id, held_on)
);

CREATE TABLE IF NOT EXISTS presence (
    class_day_id uuid NOT NULL REFERENCES class_day (id),
    student text NOT NULL,
    present boolean NOT NULL,
    PRIMARY KEY (class_day_id, student),
    CONSTRAINT presence_student_not_blank CHECK (length(btrim(student)) > 0)
);

CREATE TABLE IF NOT EXISTS announcement (
    id uuid PRIMARY KEY,
    cohort_id uuid NOT NULL REFERENCES cohort (id),
    title text NOT NULL,
    body text NOT NULL,
    CONSTRAINT announcement_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT announcement_body_not_blank CHECK (length(btrim(body)) > 0)
);

CREATE TABLE IF NOT EXISTS certificate (
    cohort_id uuid NOT NULL REFERENCES cohort (id),
    student text NOT NULL,
    average_tenths integer NOT NULL,
    issued_on date NOT NULL,
    PRIMARY KEY (cohort_id, student),
    CONSTRAINT certificate_student_not_blank CHECK (length(btrim(student)) > 0)
);

CREATE TABLE IF NOT EXISTS attempt_answer (
    exam_id uuid NOT NULL,
    student text NOT NULL,
    question_id uuid NOT NULL REFERENCES question (id),
    choice_id uuid NOT NULL REFERENCES choice (id),
    PRIMARY KEY (exam_id, student, question_id),
    FOREIGN KEY (exam_id, student) REFERENCES attempt (exam_id, student)
);

CREATE TABLE IF NOT EXISTS reply (
    id uuid PRIMARY KEY,
    doubt_id uuid NOT NULL REFERENCES doubt (id),
    author text NOT NULL,
    body text NOT NULL,
    CONSTRAINT reply_author_not_blank CHECK (length(btrim(author)) > 0),
    CONSTRAINT reply_body_not_blank CHECK (length(btrim(body)) > 0)
);

CREATE TABLE IF NOT EXISTS completion (
    cohort_id uuid NOT NULL REFERENCES cohort (id),
    lesson_id uuid NOT NULL REFERENCES lesson (id),
    student text NOT NULL,
    CONSTRAINT completion_student_not_blank CHECK (length(btrim(student)) > 0),
    PRIMARY KEY (cohort_id, lesson_id, student)
);
