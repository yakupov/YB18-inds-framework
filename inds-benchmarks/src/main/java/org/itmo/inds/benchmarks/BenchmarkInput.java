package org.itmo.inds.benchmarks;

import org.itmo.inds.testdata.DataSets;
import org.itmo.inds.testdata.FrontsAndAddends;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A population split into its true non-domination fronts, and the points to insert into it.
 * <p>
 * Some data sets store fronts that are not exactly the non-domination levels of their points, so the fronts are
 * recomputed when loading: every point gets its true rank, and points keep their stored order within a front. The
 * arrays are the loaded ones; preparations copy them before handing them to code that may modify them.
 *
 * @param reference the data-set reference ({@link FrontsAndAddends#toString()})
 * @param k         number of objectives
 * @param fronts    true fronts in rank order (front 0 = non-dominated)
 * @param addends   points to insert, in order
 */
public record BenchmarkInput(String reference, int k, List<double[][]> fronts, List<double[]> addends) {
    public BenchmarkInput {
        fronts = List.copyOf(fronts);
        addends = List.copyOf(addends);
    }

    /**
     * @param reference {@code path#snapshot}, see {@link DataSets#snapshot(String)}
     * @return the snapshot, with recomputed fronts; its one addend is the point inserted next
     */
    public static BenchmarkInput snapshot(final String reference) {
        return withTrueFronts(DataSets.snapshot(reference));
    }

    /**
     * @param path a sequence file, see {@link DataSets#sequence(String)}
     * @return the sequence, with recomputed fronts of its population
     */
    public static BenchmarkInput sequence(final String path) {
        return withTrueFronts(DataSets.sequence(path));
    }

    /**
     * @return all population points, front by front
     */
    public List<double[]> population() {
        final List<double[]> points = new ArrayList<>();
        for (final double[][] front : fronts) {
            points.addAll(Arrays.asList(front));
        }
        return points;
    }

    private static BenchmarkInput withTrueFronts(final FrontsAndAddends stored) {
        final List<double[]> points = Arrays.asList(stored.population());
        final int[] ranks = ranks(points);
        final int frontCount = Arrays.stream(ranks).max().orElse(-1) + 1;
        final List<List<double[]>> fronts = new ArrayList<>();
        for (int rank = 0; rank < frontCount; ++rank) {
            fronts.add(new ArrayList<>());
        }
        for (int i = 0; i < points.size(); ++i) {
            fronts.get(ranks[i]).add(points.get(i));
        }
        final List<double[][]> frontArrays = new ArrayList<>();
        for (final List<double[]> front : fronts) {
            frontArrays.add(front.toArray(new double[0][]));
        }
        return new BenchmarkInput(stored.toString(), stored.k(), frontArrays, stored.addends());
    }

    /**
     * Non-domination ranks by dynamic programming in lexicographic order, where every point comes after all points
     * that dominate it. Quadratic, independent of the algorithms under test.
     */
    private static int[] ranks(final List<double[]> points) {
        final Integer[] lexOrder = new Integer[points.size()];
        for (int i = 0; i < lexOrder.length; ++i) {
            lexOrder[i] = i;
        }
        Arrays.sort(lexOrder, Comparator.comparing(points::get, BenchmarkInput::compareLexicographically));
        final int[] ranks = new int[points.size()];
        for (int later = 0; later < lexOrder.length; ++later) {
            final int point = lexOrder[later];
            for (int earlier = 0; earlier < later; ++earlier) {
                final int candidate = lexOrder[earlier];
                if (ranks[candidate] + 1 > ranks[point] && dominates(points.get(candidate), points.get(point))) {
                    ranks[point] = ranks[candidate] + 1;
                }
            }
        }
        return ranks;
    }

    /**
     * Lexicographic order with the numeric {@code <} of dominance ({@code -0.0} equals {@code 0.0}), so that a point
     * never comes before a point that dominates it.
     */
    static int compareLexicographically(final double[] first, final double[] second) {
        for (int i = 0; i < first.length; ++i) {
            if (first[i] < second[i]) {
                return -1;
            }
            if (first[i] > second[i]) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Pareto dominance, minimization: {@code first} is nowhere worse than {@code second} and somewhere better.
     */
    private static boolean dominates(final double[] first, final double[] second) {
        boolean better = false;
        for (int i = 0; i < first.length; ++i) {
            if (first[i] > second[i]) {
                return false;
            }
            if (first[i] < second[i]) {
                better = true;
            }
        }
        return better;
    }
}
