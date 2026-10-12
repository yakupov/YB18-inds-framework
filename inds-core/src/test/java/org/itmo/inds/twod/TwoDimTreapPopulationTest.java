package org.itmo.inds.twod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Behaviour of the 2D treap population that the oracle cross-check does not cover: the defects of the cited code fixed
 * in this library ({@code docs/changes-from-cited-code.md}).
 */
class TwoDimTreapPopulationTest {
    /**
     * Every {@code toString} that reaches a point prints its coordinates; the cited code threw
     * {@link java.util.IllegalFormatConversionException}.
     */
    @Test
    void toStringPrintsCoordinates() {
        final TwoDimPoint point = new TwoDimPoint(0, 1);
        assertEquals("ind 0.0 1.0", point.toString());
        assertEquals("[ind 0.0 1.0]; ", new TwoDimTreap(point, 0, null, null).toString());
        final TwoDimTreapPopulation population = new TwoDimTreapPopulation();
        population.addPoint(point);
        assertEquals("0\n[ind 0.0 1.0]; \n", population.toString());
    }
}
