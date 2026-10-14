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

    private static String pdf(byte[] media) {
        byte[] header = "%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (media.length < header.length || !Arrays.equals(media, 0, header.length, header, 0, header.length)) {
            return null;
        }
        return "application/pdf";
    }

    private static String videoType(byte[] media) {
        if (media.length >= 4
                && (media[0] & 0xff) == 0x1a
                && (media[1] & 0xff) == 0x45
                && (media[2] & 0xff) == 0xdf
                && (media[3] & 0xff) == 0xa3) {
            return "video/webm";
        }
        if (media.length >= 8 && media[4] == 'f' && media[5] == 't' && media[6] == 'y' && media[7] == 'p') {
            return "video/mp4";
        }
        return null;
    }

    @FunctionalInterface
    private interface Magic {
        String detect(byte[] media);
    }

}
