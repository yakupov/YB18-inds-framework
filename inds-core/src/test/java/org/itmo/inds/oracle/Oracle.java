package org.itmo.inds.oracle;

import ru.ifmo.nds.JensenFortinBuzdalov;
import ru.ifmo.nds.NonDominatedSorting;

/**
 * Test oracle: ranks computed by M. Buzdalov's independent implementation of the Jensen-Fortin-Buzdalov
 * divide-and-conquer non-dominated sorting ({@code com.github.mbuzdalov:non-dominated-sorting-implementations}, MIT).
 * Rank 0 = non-dominated. Minimization of all objectives.
 */
public final class Oracle {
    private Oracle() {
    }

    public static int[] ranks(double[][] points) {
        final int[] ranks = new int[points.length];
        if (points.length == 0) {
            return ranks;
        }
        try (final NonDominatedSorting sorting = JensenFortinBuzdalov.getRedBlackTreeSweepImplementation(1)
                .getInstance(points.length, points[0].length)) {
            sorting.sort(points, ranks);
        }
        return ranks;
    }
}
