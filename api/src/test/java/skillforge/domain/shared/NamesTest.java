package skillforge.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void trimsANameAndKeepsIt() {
        assertEquals("Oficina", Names.require("  Oficina  ", Failure.BLANK_TITLE));
    }

    @Test
    void refusesABlankOrOversizedName() {
        assertEquals("blank-title", failure(null));
        assertEquals("blank-title", failure("   "));
        assertEquals("too-large", failure("a".repeat(Names.MAX + 1)));
        assertEquals("a".repeat(Names.MAX), Names.require("a".repeat(Names.MAX), Failure.BLANK_TITLE));
    }

    private static String failure(String value) {
        return assertThrows(Rejection.class, () -> Names.require(value, Failure.BLANK_TITLE)).failure().wire();
    }

}
