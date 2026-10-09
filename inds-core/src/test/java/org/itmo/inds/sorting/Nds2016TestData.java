package org.itmo.inds.sorting;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Test-data helpers used by the nds2016 sorter tests, in place of the helpers of the vendored (unlicensed)
 * {@code ru.itmo.mbuzdalov} test code, which is not copied into this repository.
 */
final class Nds2016TestData {
    private Nds2016TestData() {
    }

    /**
     * {@code n} points of dimension {@code k}, all coordinates equal to {@code value}.
     */
    static double[][] arrayFill(int n, int k, double value) {
        final double[][] points = new double[n][k];
        for (final double[] point : points) {
            Arrays.fill(point, value);
        }
        return points;
    }

    static int[] arrayFill(int n, int value) {
        final int[] values = new int[n];
        Arrays.fill(values, value);
        return values;
    }

    static double[][] concat(double[][] first, double[][] second) {
        final double[][] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    static int[] concat(int[] first, int[] second) {
        final int[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    /**
     * The integer grid {0, ..., size - 1}^dim in a seeded random order, with its ranks: the rank of a grid point is
     * the sum of its coordinates.
     */
    static void hypercube(int dim, int size, BiConsumer<double[][], int[]> test) {
        final int n = (int) Math.pow(size, dim);
        final double[][] points = new double[n][dim];
        for (int i = 0; i < n; ++i) {
            for (int j = 0, rest = i; j < dim; ++j, rest /= size) {
                points[i][j] = rest % size;
            }
        }
        Collections.shuffle(Arrays.asList(points), new Random(366239L + 31L * dim + size));
        final int[] ranks = new int[n];
        for (int i = 0; i < n; ++i) {
            ranks[i] = (int) Arrays.stream(points[i]).sum();
        }
        test.accept(points, ranks);
    }
}
