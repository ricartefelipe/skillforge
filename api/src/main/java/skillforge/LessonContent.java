package skillforge;

import java.util.Arrays;

public final class LessonContent {

    public record Limits(int markdownMax, long pdfMax, long videoMax) {
    }

    public sealed interface Decision {
    }

    public record Accepted(String kind, String markdown, byte[] media, String mediaType) implements Decision {
    }

    public record Rejected(String code) implements Decision {
    }

    private LessonContent() {
    }

    public static Decision decide(String kind, String markdown, byte[] media, Limits limits) {
        if (kind == null) {
            return new Rejected("unknown-kind");
        }
        return switch (kind) {
            case "markdown" -> markdown(markdown, media, limits.markdownMax());
            case "pdf" -> file(markdown, media, limits.pdfMax(), "application/pdf", "not-pdf", LessonContent::pdf);
            case "video" -> file(markdown, media, limits.videoMax(), null, "not-video", LessonContent::videoType);
            default -> new Rejected("unknown-kind");
        };
    }

    private static Decision markdown(String markdown, byte[] media, int max) {
        boolean hasFile = media != null && media.length > 0;
        if (hasFile || markdown == null || markdown.isBlank()) {
            return new Rejected("markdown-only");
        }
        if (markdown.length() > max) {
            return new Rejected("too-large");
        }
        return new Accepted("markdown", markdown, null, null);
    }

    private static Decision file(
            String markdown,
            byte[] media,
            long max,
            String fixedType,
            String invalidCode,
            Magic magic) {
        if (markdown != null && !markdown.isBlank()) {
            return new Rejected("file-only");
        }
        if (media == null || media.length == 0) {
            return new Rejected(invalidCode);
        }
        if (media.length > max) {
            return new Rejected("too-large");
        }
        String detected = magic.detect(media);
        if (detected == null) {
            return new Rejected(invalidCode);
        }
        String mediaType = fixedType == null ? detected : fixedType;
        return new Accepted(fixedType == null ? "video" : "pdf", null, media, mediaType);
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
