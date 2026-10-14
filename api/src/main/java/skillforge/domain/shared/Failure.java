package skillforge.domain.shared;

public enum Failure {

    BLANK_COURSE("blank-course", Kind.BAD_REQUEST),
    BLANK_MODULE("blank-module", Kind.BAD_REQUEST),
    BLANK_TITLE("blank-title", Kind.BAD_REQUEST),
    BLANK_COHORT("blank-cohort", Kind.BAD_REQUEST),
    BLANK_TEACHER("blank-teacher", Kind.BAD_REQUEST),
    BLANK_STUDENT("blank-student", Kind.BAD_REQUEST),
    BLANK_PRACTICE("blank-practice", Kind.BAD_REQUEST),
    BLANK_PROMPT("blank-prompt", Kind.BAD_REQUEST),
    BLANK_SUBMISSION("blank-submission", Kind.BAD_REQUEST),
    BLANK_FEEDBACK("blank-feedback", Kind.BAD_REQUEST),
    TOO_LARGE("too-large", Kind.BAD_REQUEST),
    UNKNOWN_KIND("unknown-kind", Kind.BAD_REQUEST),
    MARKDOWN_ONLY("markdown-only", Kind.BAD_REQUEST),
    NOT_PDF("not-pdf", Kind.BAD_REQUEST),
    NOT_VIDEO("not-video", Kind.BAD_REQUEST),
    FILE_ONLY("file-only", Kind.BAD_REQUEST),
    DUPLICATE_COURSE("duplicate-course", Kind.CONFLICT),
    DUPLICATE_COHORT("duplicate-cohort", Kind.CONFLICT),
    ALREADY_ENROLLED("already-enrolled", Kind.CONFLICT),
    DUPLICATE_PRACTICE("duplicate-practice", Kind.CONFLICT),
    ALREADY_RETURNED("already-returned", Kind.CONFLICT),
    NOT_FOUND("not-found", Kind.NOT_FOUND),
    FORBIDDEN("forbidden", Kind.FORBIDDEN);

    private final String wire;

    private final Kind kind;

    Failure(String wire, Kind kind) {
        this.wire = wire;
        this.kind = kind;
    }

    public String wire() {
        return wire;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        BAD_REQUEST,
        CONFLICT,
        NOT_FOUND,
        FORBIDDEN
    }

}
