package skillforge.domain.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class GradeAverageTest {

    @Test
    void equalWeightsKeepTheFlatMean() {
        OptionalInt tenths = GradeAverage.tenths(List.of(8), List.of(10), 1, 1);

        assertEquals(90, tenths.orElseThrow());
        assertEquals("9.0", GradeAverage.format(tenths.orElseThrow()));
    }

    @Test
    void differentWeightsUseEachCategoryOnce() {
        OptionalInt tenths = GradeAverage.tenths(List.of(8, 9), List.of(10, 10), 2, 1);

        assertEquals(90, tenths.orElseThrow());
        assertEquals("9.0", GradeAverage.format(tenths.orElseThrow()));
    }

    @Test
    void roundsTheMixedMeanToOneDecimal() {
        assertEquals("8.7", GradeAverage.format(GradeAverage.tenths(List.of(8), List.of(10), 2, 1).orElseThrow()));
    }

    @Test
    void aMissingCategoryDoesNotInventAZero() {
        assertEquals(80, GradeAverage.tenths(List.of(8), List.of(), 2, 1).orElseThrow());
        assertTrue(GradeAverage.tenths(List.of(), List.of(), 1, 1).isEmpty());
    }

    @Test
    void theCertificateNeedsSevenAndThreeQuartersOfTheCalls() {
        assertTrue(GradeAverage.earnsCertificate(70, 0, 0));
        assertTrue(GradeAverage.earnsCertificate(70, 4, 3));
        assertFalse(GradeAverage.earnsCertificate(69, 0, 0));
        assertFalse(GradeAverage.earnsCertificate(90, 2, 1));
        assertFalse(GradeAverage.presentEnough(3, 2));
    }

}
