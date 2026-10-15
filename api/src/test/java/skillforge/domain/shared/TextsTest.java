package skillforge.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TextsTest {

    @Test
    void trimsATextAndKeepsIt() {
        assertEquals("A peça.", Texts.require("  A peça.  ", Failure.BLANK_SUBMISSION));
    }

    @Test
    void refusesABlankOrOversizedText() {
        assertEquals("blank-submission", failure(null));
        assertEquals("blank-submission", failure(" "));
        assertEquals("too-large", failure("a".repeat(Texts.MAX + 1)));
    }

    private static String failure(String value) {
        return assertThrows(Rejection.class, () -> Texts.require(value, Failure.BLANK_SUBMISSION)).failure().wire();
    }

}
