package org.itmo.inds.oracle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class OracleTest {
    @Test
    void ranksOfSmallSet() {
        final double[][] points = {
                {1, 4}, {2, 2}, {4, 1},   // level 0
                {3, 3}, {2, 5},           // level 1 ({3,3} dominated by {2,2}; {2,5} by {1,4})
                {4, 4},                   // level 2 (dominated by {3,3})
                {2, 2},                   // duplicate of a level-0 point
        };
        assertArrayEquals(new int[]{0, 0, 0, 1, 1, 2, 0}, Oracle.ranks(points));
    }

    @Test
    void emptyInput() {
        assertArrayEquals(new int[0], Oracle.ranks(new double[0][]));
    }
}
