package skillforge.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MarksTest {

    @Test
    void keepsAMarkFromZeroToTen() {
        assertEquals(0, Marks.require(0));
        assertEquals(10, Marks.require(10));
    }

    @Test
    void refusesAMissingOrOutOfRangeMark() {
        assertEquals("invalid-grade", failure(null));
        assertEquals("invalid-grade", failure(-1));
        assertEquals("invalid-grade", failure(11));
    }

    private static String failure(Integer value) {
        return assertThrows(Rejection.class, () -> Marks.require(value)).failure().wire();
    }

}
