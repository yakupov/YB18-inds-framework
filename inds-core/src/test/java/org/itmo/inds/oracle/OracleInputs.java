package org.itmo.inds.oracle;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Seeded inputs for the oracle cross-checks: uniform random sets of several dimensions and sizes, duplicates and ties,
 * and degenerate shapes. Points are listed in insertion order; {@code k} is the number of objectives, {@code n} the
 * number of points.
 */
public final class OracleInputs {
    /**
     * A named point set.
     */
    public record Case(String name, double[][] points) {
        @Override
        @Nonnull
        public String toString() {
            return name;
        }
    }

    private OracleInputs() {
    }

    public static List<Case> cases() {
        final List<Case> cases = new ArrayList<>();
        for (final int k : new int[]{2, 3, 5, 8}) {
            for (final int n : new int[]{1, 2, 10, 100, 1000}) {
                cases.add(new Case("uniform-k" + k + "-n" + n, uniform(k, n, 1000L * k + n)));
            }
            cases.add(new Case("duplicates-k" + k, duplicates(k, 300, 7L * k)));
            cases.add(new Case("ties-k" + k, ties(k, 300, 11L * k)));
            cases.add(new Case("all-equal-k" + k, allEqual(k, 20)));
            cases.add(new Case("chain-best-first-k" + k, chain(k, 50, true)));
            cases.add(new Case("chain-worst-first-k" + k, chain(k, 50, false)));
            cases.add(new Case("simplex-k" + k, layers(k, 200, 1, 13L * k)));
            cases.add(new Case("two-simplices-k" + k, layers(k, 200, 2, 17L * k)));
        }
        return cases;
    }

    static double[][] uniform(final int k, final int n, final long seed) {
        final Random random = new Random(seed);
        final double[][] points = new double[n][k];
        for (final double[] point : points) {
            for (int j = 0; j < k; ++j) {
                point[j] = random.nextDouble();
            }
        }
        return points;
    }

    /**
     * About a third of the points are exact copies of earlier ones.
     */
    static double[][] duplicates(final int k, final int n, final long seed) {
        final Random random = new Random(seed);
        final double[][] points = new double[n][];
        for (int i = 0; i < n; ++i) {
            if (i > 0 && random.nextInt(3) == 0) {
                points[i] = points[random.nextInt(i)].clone();
            } else {
                points[i] = new double[k];
                for (int j = 0; j < k; ++j) {
                    points[i][j] = random.nextDouble();
                }
            }
        }
        return points;
    }

    /**
     * Coordinates on a grid of 5 values: many ties in every objective, many duplicates.
     */
    static double[][] ties(final int k, final int n, final long seed) {
        final Random random = new Random(seed);
        final double[][] points = new double[n][k];
        for (final double[] point : points) {
            for (int j = 0; j < k; ++j) {
                point[j] = random.nextInt(5);
            }
        }
        return points;
    }

    static double[][] allEqual(final int k, final int n) {
        final double[][] points = new double[n][k];
        for (final double[] point : points) {
            Arrays.fill(point, 0.5);
        }
        return points;
    }

    /**
     * A dominance chain: point i dominates point i + 1 ({@code bestFirst}) or point i - 1.
     */
    static double[][] chain(final int k, final int n, final boolean bestFirst) {
        final double[][] points = new double[n][k];
        for (int i = 0; i < n; ++i) {
            Arrays.fill(points[bestFirst ? i : n - 1 - i], i);
        }
        return points;
    }

    /**
     * Points on {@code layers} parallel simplices (sum of coordinates = layer + 1), shuffled.
     */
    static double[][] layers(final int k, final int n, final int layers, final long seed) {
        final Random random = new Random(seed);
        final double[][] points = new double[n][k];
        for (final double[] point : points) {
            double sum = 0;
            for (int j = 0; j < k; ++j) {
                point[j] = random.nextDouble() + 1e-9;
                sum += point[j];
            }
            final double layerSum = 1 + random.nextInt(layers);
            for (int j = 0; j < k; ++j) {
                point[j] *= layerSum / sum;
            }
        }
        return points;
    }
}
