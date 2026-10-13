package org.itmo.inds.testdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticProblemTest {
    private static final int N = 1200;

    @ParameterizedTest
    @EnumSource(SyntheticProblem.class)
    void pointsAreTwoDimensionalAndSeededOnlyIfRandom(final SyntheticProblem problem) {
        final double[][] points = problem.points(N, 1);
        assertEquals(N, points.length);
        assertTrue(Arrays.stream(points).allMatch(point -> point.length == 2));
        assertEquals(problem.isRandom(), !Arrays.deepEquals(points, problem.points(N, 2)));
        assertTrue(Arrays.deepEquals(points, problem.points(N, 1)));
    }

    @Test
    void squareAndParallelStayInTheirRegion() {
        for (final double[] point : SyntheticProblem.SQUARE.points(N, 0)) {
            assertTrue(point[0] >= 0 && point[0] < N && point[1] >= 0 && point[1] < N);
        }
        int onFirstLine = 0;
        for (final double[] point : SyntheticProblem.PARALLEL.points(N, 0)) {
            final double offset = point[0] + point[1] - N;
            assertTrue(offset == 0 || offset == 1);
            onFirstLine += offset == 0 ? 1 : 0;
        }
        assertEquals(N / 2, onFirstLine);
    }

    @Test
    void diagonalsStartFromTheBiggestX() {
        final double[][] diag1 = SyntheticProblem.DIAG1.points(4, 0);
        assertArrayEquals(new double[]{3, 3}, diag1[0]);
        assertArrayEquals(new double[]{0, 0}, diag1[3]);
        final double[][] diag2 = SyntheticProblem.DIAG2.points(4, 0);
        assertArrayEquals(new double[]{1, 6}, diag2[0]);
        assertArrayEquals(new double[]{6, 1}, diag2[1]);
        assertArrayEquals(new double[]{0, 5}, diag2[2]);
        assertArrayEquals(new double[]{5, 0}, diag2[3]);
    }

    @Test
    void parperHasTheFourLinesWithTheirCounts() {
        final int[] counts = new int[4];
        for (final double[] point : SyntheticProblem.PARPER.points(N, 0)) {
            final double x = point[0];
            final double y = point[1];
            if (y == x + 5) {
                ++counts[0];
            } else if (y == x - 5) {
                ++counts[1];
            } else if (y == N / 3 - x - 4) {
                ++counts[2];
            } else {
                assertEquals(N / 3 - x - 6, y);
                ++counts[3];
            }
        }
        assertArrayEquals(new int[]{N / 6, N / 6, N / 3, N / 3}, counts);
        assertFalse(SyntheticProblem.PARPER.isRandom());
    }
}
