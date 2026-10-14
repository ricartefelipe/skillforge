package skillforge.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class LessonContentTest {

    private static final LessonContent.Limits LIMITS = new LessonContent.Limits(12, 32, 32);

    @Test
    void markdownKeepsTheTextAndNothingElse() {
        LessonContent.Decision decision = LessonContent.decide("markdown", "Oficina", null, LIMITS);

        LessonContent.Accepted accepted = assertInstanceOf(LessonContent.Accepted.class, decision);
        assertEquals(LessonKind.MARKDOWN, accepted.kind());
        assertEquals("Oficina", accepted.markdown());
    }

    @Test
    void markdownRejectsABlankTextOrAFile() {
        assertEquals("markdown-only", code(LessonContent.decide("markdown", "  ", null, LIMITS)));
        assertEquals("markdown-only", code(LessonContent.decide("markdown", "Oficina", new byte[] {1}, LIMITS)));
    }

    @Test
    void markdownRejectsTextPastTheLimit() {
        assertEquals("too-large", code(LessonContent.decide("markdown", "1234567890123", null, LIMITS)));
    }

    @Test
    void pdfAcceptsARealHeaderAndRejectsAnythingElse() {
        byte[] pdf = "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        LessonContent.Accepted accepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("pdf", null, pdf, LIMITS));
        assertEquals("application/pdf", accepted.mediaType());

        assertEquals("not-pdf", code(LessonContent.decide("pdf", null, "hello".getBytes(), LIMITS)));
        assertEquals("file-only", code(LessonContent.decide("pdf", "notas", pdf, LIMITS)));
    }

    @Test
    void pdfRejectsAFilePastTheLimit() {
        byte[] pdf = new byte[33];
        pdf[0] = '%';
        pdf[1] = 'P';
        pdf[2] = 'D';
        pdf[3] = 'F';
        assertEquals("too-large", code(LessonContent.decide("pdf", null, pdf, LIMITS)));
    }

    @Test
    void videoAcceptsMp4AndWebm() {
        byte[] mp4 = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'};
        byte[] webm = new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3};

        LessonContent.Accepted mp4Accepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("video", null, mp4, LIMITS));
        assertEquals("video/mp4", mp4Accepted.mediaType());

        LessonContent.Accepted webmAccepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("video", " ", webm, LIMITS));
        assertEquals("video/webm", webmAccepted.mediaType());

        assertEquals("not-video", code(LessonContent.decide("video", null, "%PDF".getBytes(), LIMITS)));
        assertEquals("unknown-kind", code(LessonContent.decide("slide", null, mp4, LIMITS)));
    }

    @Test
    void acceptedMediaIsADefensiveCopy() {
        byte[] pdf = "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        LessonContent.Accepted accepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("pdf", null, pdf, LIMITS));

        pdf[0] = 'X';
        accepted.media()[0] = 'Y';

        assertEquals((byte) '%', accepted.media()[0]);
    }

    private static String code(LessonContent.Decision decision) {
        return assertInstanceOf(LessonContent.Rejected.class, decision).code();
    }

}
