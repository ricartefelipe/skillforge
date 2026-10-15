package skillforge.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LessonKindTest {

    @Test
    void parsesEveryPublishedKindAndNothingElse() {
        assertEquals(LessonKind.MARKDOWN, LessonKind.parse("markdown").orElseThrow());
        assertEquals(LessonKind.PDF, LessonKind.parse("pdf").orElseThrow());
        assertEquals(LessonKind.EBOOK, LessonKind.parse("ebook").orElseThrow());
        assertEquals(LessonKind.VIDEO, LessonKind.parse("video").orElseThrow());
        assertEquals(LessonKind.SCORM, LessonKind.parse("scorm").orElseThrow());
        assertEquals(LessonKind.LTI, LessonKind.parse("lti").orElseThrow());
        assertTrue(LessonKind.parse(null).isEmpty());
        assertTrue(LessonKind.parse("pagina").isEmpty());
    }

}
