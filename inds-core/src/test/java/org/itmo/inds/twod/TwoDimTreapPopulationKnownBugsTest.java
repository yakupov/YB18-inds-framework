package org.itmo.inds.twod;

import org.junit.jupiter.api.Test;

import java.util.IllegalFormatConversionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Records the known defects of the 2D treap population as they are in the cited code: they are documented, not fixed.
 * A test here failing means the behaviour changed; a deliberate fix updates the test.
 */
class TwoDimTreapPopulationKnownBugsTest {
    /**
     * {@code String.format("ind %d %d", x1, x2)} with doubles: every {@code toString} that reaches a point throws.
     */
    @Test
    void toStringThrows() {
        final TwoDimPoint point = new TwoDimPoint(0, 1);
        assertThrows(IllegalFormatConversionException.class, point::toString);
        assertThrows(IllegalFormatConversionException.class, new TwoDimTreap(point, 0, null, null)::toString);
        final TwoDimTreapPopulation population = new TwoDimTreapPopulation();
        population.addPoint(point);
        assertThrows(IllegalFormatConversionException.class, population::toString);
    }

    /**
     * {@code detRankOfExPoint} accepts a result only if it is {@code > 0} after the binary search. With two or more
     * levels, rank-0 points are found inside the loop and the method is correct (oracle cross-check); with exactly
     * one level the loop is skipped and every point ends in the "Can't determine rank" exception, whose message
     * calls the broken {@code toString}, so {@code getRandWithRank} throws {@link IllegalFormatConversionException}.
     */
    @Test
    void detRankOfExPointThrowsWithOneLevel() {
        final TwoDimTreapPopulation population = new TwoDimTreapPopulation();
        final TwoDimPoint first = new TwoDimPoint(0, 1);
        final TwoDimPoint second = new TwoDimPoint(1, 0);
        population.addPoint(first);
        population.addPoint(second);
        assertThrows(IllegalFormatConversionException.class, () -> population.detRankOfExPoint(first));
        assertThrows(IllegalFormatConversionException.class, () -> population.detRankOfExPoint(second));
        assertThrows(IllegalFormatConversionException.class, population::getRandWithRank);

        population.addPoint(new TwoDimPoint(2, 2)); // second level: rank 0 is found
        assertEquals(0, population.detRankOfExPoint(first));
        assertEquals(0, population.detRankOfExPoint(second));
        assertEquals(1, population.detRankOfExPoint(new TwoDimPoint(2, 2)));
    }

    /**
     * A static, unsynchronized counter {@code dominationComparsionCount} is incremented by every comparison
     * ({@code compareX1}, {@code compareX2}, {@code compareDom}, {@code equals}), shared by all populations and
     * threads.
     */
    @Test
    void staticComparisonCounter() {
        final TwoDimPoint first = new TwoDimPoint(0, 1);
        final TwoDimPoint second = new TwoDimPoint(1, 0);
        final int before = TwoDimPoint.dominationComparsionCount;
        first.compareX1(second);
        first.compareX2(second);
        first.compareDom(second);
        final boolean equal = first.equals(second);
        assertFalse(equal);
        assertEquals(4, TwoDimPoint.dominationComparsionCount - before);
    }
}
