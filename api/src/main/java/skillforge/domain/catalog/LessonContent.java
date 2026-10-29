package skillforge.domain.catalog;

import java.util.Arrays;
import java.util.Objects;

import skillforge.domain.shared.Failure;

public final class LessonContent {

    public record Limits(int markdownMax, long pdfMax, long videoMax) {
    }

    public sealed interface Decision {
    }

    public record Accepted(LessonKind kind, String markdown, long mediaSize, String mediaType) implements Decision {

        public Accepted {
            Objects.requireNonNull(kind, "kind");
            if (mediaSize < 0) {
                throw new IllegalArgumentException("mediaSize");
            }
        }

        public boolean hasMedia() {
            return mediaSize > 0;
        }

    }

    public record Rejected(Failure failure) implements Decision {

        public Rejected {
            Objects.requireNonNull(failure, "failure");
        }

        public String code() {
            return failure.wire();
        }

    }

    private LessonContent() {
    }

    public static Decision decide(String kind, String markdown, MediaProbe media, Limits limits) {
        Objects.requireNonNull(limits, "limits");
        LessonKind parsed = LessonKind.parse(kind).orElse(null);
        if (parsed == null) {
            return new Rejected(Failure.UNKNOWN_KIND);
        }
        return switch (parsed) {
            case MARKDOWN -> markdown(markdown, media, limits.markdownMax());
            case PDF -> file(
                    markdown, media, limits.pdfMax(), "application/pdf", Failure.NOT_PDF, LessonContent::pdf, parsed);
            case EBOOK -> ebook(markdown, media, limits.pdfMax());
            case SCORM -> file(
                    markdown,
                    media,
                    limits.pdfMax(),
                    "application/zip",
                    Failure.NOT_PACKAGE,
                    LessonContent::zip,
                    parsed);
            case LTI -> external(markdown, media);
            case VIDEO -> file(
                    markdown, media, limits.videoMax(), null, Failure.NOT_VIDEO, LessonContent::videoType, parsed);
        };
    }

    private static Decision markdown(String markdown, MediaProbe media, int max) {
        if (hasFile(media) || markdown == null || markdown.isBlank()) {
            return new Rejected(Failure.MARKDOWN_ONLY);
        }
        if (markdown.length() > max) {
            return new Rejected(Failure.TOO_LARGE);
        }
        return new Accepted(LessonKind.MARKDOWN, markdown, 0, null);
    }

    private static Decision file(
            String markdown,
            MediaProbe media,
            long max,
            String fixedType,
            Failure invalid,
            Magic magic,
            LessonKind kind) {
        if (markdown != null && !markdown.isBlank()) {
            return new Rejected(Failure.FILE_ONLY);
        }
        if (!hasFile(media)) {
            return new Rejected(invalid);
        }
        if (media.size() > max) {
            return new Rejected(Failure.TOO_LARGE);
        }
        String detected = magic.detect(media.head());
        if (detected == null) {
            return new Rejected(invalid);
        }
        String mediaType = fixedType == null ? detected : fixedType;
        return new Accepted(kind, null, media.size(), mediaType);
    }

    private static Decision ebook(String markdown, MediaProbe media, long max) {
        if (markdown != null && !markdown.isBlank()) {
            return new Rejected(Failure.FILE_ONLY);
        }
        if (!hasFile(media)) {
            return new Rejected(Failure.NOT_EBOOK);
        }
        byte[] head = media.head();
        if (pdf(head) == null && epub(head) == null) {
            return new Rejected(Failure.NOT_EBOOK);
        }
        if (pdf(head) != null) {
            return file(null, media, max, "application/pdf", Failure.NOT_EBOOK, LessonContent::pdf, LessonKind.PDF);
        }
        return file(null, media, max, "application/epub+zip", Failure.NOT_EBOOK, LessonContent::epub, LessonKind.EBOOK);
    }

    private static boolean hasFile(MediaProbe media) {
        return media != null && !media.isEmpty();
    }

    private static String pdf(byte[] head) {
        byte[] magic = "%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (head.length < magic.length || !Arrays.equals(head, 0, magic.length, magic, 0, magic.length)) {
            return null;
        }
        return "application/pdf";
    }

    private static String epub(byte[] head) {
        if (!isZip(head)) {
            return null;
        }
        int window = Math.min(head.length, 200);
        String start = new String(head, 0, window, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!start.contains("application/epub+zip")) {
            return null;
        }
        return "application/epub+zip";
    }

    private static Decision external(String markdown, MediaProbe media) {
        if (hasFile(media) || markdown != null && !markdown.isBlank()) {
            return new Rejected(Failure.FILE_ONLY);
        }
        return new Accepted(LessonKind.LTI, null, 0, null);
    }

    private static String zip(byte[] head) {
        if (!isZip(head)) {
            return null;
        }
        return "application/zip";
    }

    private static boolean isZip(byte[] head) {
        return head.length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
    }

    private static String videoType(byte[] head) {
        if (head.length >= 4
                && (head[0] & 0xff) == 0x1a
                && (head[1] & 0xff) == 0x45
                && (head[2] & 0xff) == 0xdf
                && (head[3] & 0xff) == 0xa3) {
            return "video/webm";
        }
        int last = Math.min(head.length - 4, 64);
        for (int index = 0; index <= last; index++) {
            if (head[index] == 'f' && head[index + 1] == 't' && head[index + 2] == 'y' && head[index + 3] == 'p') {
                return "video/mp4";
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface Magic {
        String detect(byte[] head);
    }

}
