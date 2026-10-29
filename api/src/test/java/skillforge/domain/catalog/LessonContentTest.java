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
        assertEquals(
                "markdown-only",
                code(LessonContent.decide("markdown", "Oficina", MediaProbe.of(new byte[] {1}), LIMITS)));
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
                LessonContent.decide("pdf", null, MediaProbe.of(pdf), LIMITS));
        assertEquals("application/pdf", accepted.mediaType());

        assertEquals("not-pdf", code(LessonContent.decide("pdf", null, MediaProbe.of("hello".getBytes()), LIMITS)));
        assertEquals("file-only", code(LessonContent.decide("pdf", "notas", MediaProbe.of(pdf), LIMITS)));
    }

    @Test
    void pdfRejectsAFilePastTheLimit() {
        byte[] pdf = new byte[33];
        pdf[0] = '%';
        pdf[1] = 'P';
        pdf[2] = 'D';
        pdf[3] = 'F';
        assertEquals("too-large", code(LessonContent.decide("pdf", null, MediaProbe.of(pdf), LIMITS)));
    }

    @Test
    void ebookAcceptsPdfAndEpub() {
        byte[] pdf = "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        LessonContent.Accepted pdfAccepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("ebook", null, MediaProbe.of(pdf), LIMITS));
        assertEquals(LessonKind.PDF, pdfAccepted.kind());

        byte[] epub = "PK\u0003\u0004application/epub+zip".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        LessonContent.Accepted epubAccepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("ebook", null, MediaProbe.of(epub), LIMITS));
        assertEquals(LessonKind.EBOOK, epubAccepted.kind());
        assertEquals("application/epub+zip", epubAccepted.mediaType());

        assertEquals(
                "not-ebook",
                code(LessonContent.decide("ebook", null, MediaProbe.of("PK\u0003\u0004notas".getBytes()), LIMITS)));
    }

    @Test
    void videoAcceptsMp4AndWebm() {
        byte[] mp4 = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'};
        byte[] webm = new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3};

        LessonContent.Accepted mp4Accepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("video", null, MediaProbe.of(mp4), LIMITS));
        assertEquals("video/mp4", mp4Accepted.mediaType());

        LessonContent.Accepted webmAccepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("video", " ", MediaProbe.of(webm), LIMITS));
        assertEquals("video/webm", webmAccepted.mediaType());

        byte[] later = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 'f', 't', 'y', 'p'};
        LessonContent.Accepted laterAccepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("video", null, MediaProbe.of(later), LIMITS));
        assertEquals("video/mp4", laterAccepted.mediaType());

        assertEquals("not-video", code(LessonContent.decide("video", null, MediaProbe.of("%PDF".getBytes()), LIMITS)));
        assertEquals("unknown-kind", code(LessonContent.decide("slide", null, MediaProbe.of(mp4), LIMITS)));
    }

    @Test
    void theProbedStartIsADefensiveCopy() {
        byte[] pdf = "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MediaProbe probe = MediaProbe.of(pdf);

        pdf[0] = 'X';
        probe.head()[1] = 'Y';

        assertEquals((byte) '%', probe.head()[0]);
        assertEquals((byte) 'P', probe.head()[1]);
    }

    @Test
    void theDecisionReadsOnlyTheStartAndTheDeclaredSize() {
        byte[] start = "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        LessonContent.Accepted accepted = assertInstanceOf(
                LessonContent.Accepted.class,
                LessonContent.decide("pdf", null, new MediaProbe(start, 32), LIMITS));

        assertEquals(32, accepted.mediaSize());
        assertEquals("application/pdf", accepted.mediaType());
    }

    @Test
    void aFileBeyondTheCeilingIsRefusedByItsDeclaredSize() {
        byte[] start = "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        assertEquals("too-large", code(LessonContent.decide("pdf", null, new MediaProbe(start, 33), LIMITS)));
    }

    private static String code(LessonContent.Decision decision) {
        return assertInstanceOf(LessonContent.Rejected.class, decision).code();
    }

}
