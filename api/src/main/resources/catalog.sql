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
    CONSTRAINT lesson_kind CHECK (kind IN ('markdown', 'pdf', 'video')),
    -- A lesson carries markdown, or a file, and never both.
    CONSTRAINT lesson_one_content CHECK (
        (markdown IS NOT NULL) = (kind = 'markdown')
        AND (media IS NULL) = (markdown IS NOT NULL)
        AND (media_type IS NULL) = (media IS NULL)
        AND (markdown IS NULL OR length(btrim(markdown)) > 0)
    )
);
