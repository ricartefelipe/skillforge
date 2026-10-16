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
    NOT_EBOOK("not-ebook", Kind.BAD_REQUEST),
    NOT_VIDEO("not-video", Kind.BAD_REQUEST),
    NOT_PACKAGE("not-package", Kind.BAD_REQUEST),
    LAUNCH("launch", Kind.BAD_REQUEST),
    MAIL("mail", Kind.BAD_REQUEST),
    FILE_ONLY("file-only", Kind.BAD_REQUEST),
    DUPLICATE_COURSE("duplicate-course", Kind.CONFLICT),
    DUPLICATE_COHORT("duplicate-cohort", Kind.CONFLICT),
    ALREADY_ENROLLED("already-enrolled", Kind.CONFLICT),
    DUPLICATE_PRACTICE("duplicate-practice", Kind.CONFLICT),
    DUPLICATE_EXAM("duplicate-exam", Kind.CONFLICT),
    DUPLICATE_DAY("duplicate-day", Kind.CONFLICT),
    ALREADY_TAKEN("already-taken", Kind.CONFLICT),
    ALREADY_RETURNED("already-returned", Kind.CONFLICT),
    INVALID_GRADE("invalid-grade", Kind.BAD_REQUEST),
    BLANK_EXAM("blank-exam", Kind.BAD_REQUEST),
    BLANK_QUESTION("blank-question", Kind.BAD_REQUEST),
    BLANK_CHOICE("blank-choice", Kind.BAD_REQUEST),
    INVALID_EXAM("invalid-exam", Kind.BAD_REQUEST),
    RUBRIC("rubric", Kind.BAD_REQUEST),
    PAST_DUE("past-due", Kind.FORBIDDEN),
    NOT_CERTIFIED("not-certified", Kind.FORBIDDEN),
    LOCKED("locked", Kind.CONFLICT),
    NOT_STARTED("not-started", Kind.BAD_REQUEST),
    LESSONS("lessons", Kind.CONFLICT),
    DUPLICATE_GROUP("duplicate-group", Kind.CONFLICT),
    GROUPED("grouped", Kind.CONFLICT),
    ALREADY_REVIEWED("already-reviewed", Kind.CONFLICT),
    WEIGHT("weight", Kind.BAD_REQUEST),
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
