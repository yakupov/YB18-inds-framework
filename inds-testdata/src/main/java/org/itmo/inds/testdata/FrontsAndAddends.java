package org.itmo.inds.testdata;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Uniform view of a data set entry: a population split into non-domination fronts and the points to insert into it,
 * in order. A snapshot ({@code snapshots/}) has one addend, a sequence ({@code sequences/}) has many.
 * <p>
 * The arrays are the loaded ones, not copies: clone them before handing them to code that may modify them.
 *
 * @param path     resource path relative to {@link DataSets#ROOT}
 * @param snapshot snapshot id as stored (generational NSGA-II: generation; steady-state NSGA-II: iteration count;
 *                 synthetic sets: sample index);
 *                 0 for a sequence
 * @param fronts   fronts in rank order (front 0 = non-dominated), members in stored order
 * @param addends  points to insert, in order
 */
public record FrontsAndAddends(String path, int snapshot, List<double[][]> fronts, List<double[]> addends) {
    /**
     * Separates path and snapshot id in a snapshot reference ({@link #toString()}, {@link DataSets#snapshot(String)}).
     */
    public static final String SNAPSHOT_SEPARATOR = "#";

    public FrontsAndAddends {
        fronts = List.copyOf(fronts);
        addends = List.copyOf(addends);
    }

    /**
     * Number of objectives.
     */
    public int k() {
        return fronts.isEmpty() ? addends.get(0).length : fronts.get(0)[0].length;
    }

    /**
     * Number of points in the fronts.
     */
    public int populationSize() {
        int size = 0;
        for (final double[][] front : fronts) {
            size += front.length;
        }
        return size;
    }

    /**
     * All population points, front by front.
     */
    public double[][] population() {
        final double[][] population = new double[populationSize()][];
        int i = 0;
        for (final double[][] front : fronts) {
            for (final double[] point : front) {
                population[i++] = point;
            }
        }
        return population;
    }

    /**
     * @return the reference of this entry: {@code path#snapshot} for a snapshot (accepted by
     * {@link DataSets#snapshot(String)}), the path for a sequence
     */
    @Override
    @Nonnull
    public String toString() {
        return path + (path.startsWith("snapshots/") ? SNAPSHOT_SEPARATOR + snapshot : "");
    }
}
