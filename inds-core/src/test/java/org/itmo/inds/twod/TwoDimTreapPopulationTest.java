package org.itmo.inds.twod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /**
     * {@code detRankOfExPoint} and {@code getRandWithRank} work while there is only one level; the cited code
     * rejected rank 0 after the binary search, which is skipped with one level, and threw for every point.
     */
    @Test
    void detRankOfExPointWithOneLevel() {
        final TwoDimTreapPopulation population = new TwoDimTreapPopulation();
        final TwoDimPoint first = new TwoDimPoint(0, 1);
        final TwoDimPoint second = new TwoDimPoint(1, 0);
        population.addPoint(first);
        population.addPoint(second);
        assertEquals(0, population.detRankOfExPoint(first));
        assertEquals(0, population.detRankOfExPoint(second));
        assertEquals(0, population.getRandWithRank().rank);
        population.validate();
    }

    /**
     * {@code detRankOfExPoint} rejects a point that is not in the population; the cited code looped forever for some
     * of them (here: dominated by the first level, dominating a point of the second).
     */
    @Test
    void detRankOfExPointRejectsPointNotInPopulation() {
        final TwoDimTreapPopulation population = new TwoDimTreapPopulation();
        population.addPoint(new TwoDimPoint(0, 0));
        population.addPoint(new TwoDimPoint(2, 2));
        assertThrows(IllegalArgumentException.class, () -> population.detRankOfExPoint(new TwoDimPoint(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> population.detRankOfExPoint(null));
    }
}
