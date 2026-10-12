package org.itmo.inds.twod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Records the known defects of the 2D treap population as they are in the cited code: they are documented, not fixed.
 * A test here failing means the behaviour changed; a deliberate fix updates the test.
 */
class TwoDimTreapPopulationKnownBugsTest {
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
