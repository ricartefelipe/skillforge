package skillforge.domain.catalog;

import java.util.Arrays;
import java.util.Objects;

import skillforge.domain.shared.Failure;

public final class LessonContent {

    public record Limits(int markdownMax, long pdfMax, long videoMax) {
    }

    public sealed interface Decision {
    }

    public record Accepted(LessonKind kind, String markdown, byte[] media, String mediaType) implements Decision {

        public Accepted {
            Objects.requireNonNull(kind, "kind");
            media = media == null ? null : media.clone();
        }

        @Override
        public byte[] media() {
            return media == null ? null : media.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Accepted accepted)) {
                return false;
            }
            return kind == accepted.kind
                    && Objects.equals(markdown, accepted.markdown)
                    && Arrays.equals(media, accepted.media())
                    && Objects.equals(mediaType, accepted.mediaType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, markdown, Arrays.hashCode(media), mediaType);
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

    public static Decision decide(String kind, String markdown, byte[] media, Limits limits) {
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

    private static Decision markdown(String markdown, byte[] media, int max) {
        boolean hasFile = media != null && media.length > 0;
        if (hasFile || markdown == null || markdown.isBlank()) {
            return new Rejected(Failure.MARKDOWN_ONLY);
        }
        if (markdown.length() > max) {
            return new Rejected(Failure.TOO_LARGE);
        }
        return new Accepted(LessonKind.MARKDOWN, markdown, null, null);
    }

    private static Decision file(
            String markdown,
            byte[] media,
            long max,
            String fixedType,
            Failure invalid,
            Magic magic,
            LessonKind kind) {
        if (markdown != null && !markdown.isBlank()) {
            return new Rejected(Failure.FILE_ONLY);
        }
        if (media == null || media.length == 0) {
            return new Rejected(invalid);
        }
        if (media.length > max) {
            return new Rejected(Failure.TOO_LARGE);
        }
        String detected = magic.detect(media);
        if (detected == null) {
            return new Rejected(invalid);
        }
        String mediaType = fixedType == null ? detected : fixedType;
        return new Accepted(kind, null, media, mediaType);
    }

    private static Decision ebook(String markdown, byte[] media, long max) {
        if (markdown != null && !markdown.isBlank()) {
            return new Rejected(Failure.FILE_ONLY);
        }
        if (media == null || media.length == 0 || pdf(media) == null && epub(media) == null) {
            return new Rejected(Failure.NOT_EBOOK);
        }
        if (pdf(media) != null) {
            return file(null, media, max, "application/pdf", Failure.NOT_EBOOK, LessonContent::pdf, LessonKind.PDF);
        }
        return file(null, media, max, "application/epub+zip", Failure.NOT_EBOOK, LessonContent::epub, LessonKind.EBOOK);
    }

    private static String pdf(byte[] media) {
        byte[] header = "%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (media.length < header.length || !Arrays.equals(media, 0, header.length, header, 0, header.length)) {
            return null;
        }
        return "application/pdf";
    }

    private static String epub(byte[] media) {
        if (media.length < 4 || media[0] != 'P' || media[1] != 'K' || media[2] != 3 || media[3] != 4) {
            return null;
        }
        int window = Math.min(media.length, 200);
        String head = new String(media, 0, window, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!head.contains("application/epub+zip")) {
            return null;
        }
        return "application/epub+zip";
    }

    private static Decision external(String markdown, byte[] media) {
        boolean hasFile = media != null && media.length > 0;
        if (hasFile || markdown != null && !markdown.isBlank()) {
            return new Rejected(Failure.FILE_ONLY);
        }
        return new Accepted(LessonKind.LTI, null, null, null);
    }

    private static String zip(byte[] media) {
        if (media.length < 4 || media[0] != 'P' || media[1] != 'K' || media[2] != 3 || media[3] != 4) {
            return null;
        }
        return "application/zip";
    }

    private static String videoType(byte[] media) {
        if (media.length >= 4
                && (media[0] & 0xff) == 0x1a
                && (media[1] & 0xff) == 0x45
                && (media[2] & 0xff) == 0xdf
                && (media[3] & 0xff) == 0xa3) {
            return "video/webm";
        }
        int last = Math.min(media.length - 4, 64);
        for (int index = 0; index <= last; index++) {
            if (media[index] == 'f' && media[index + 1] == 't' && media[index + 2] == 'y' && media[index + 3] == 'p') {
                return "video/mp4";
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface Magic {
        String detect(byte[] media);
    }

}
