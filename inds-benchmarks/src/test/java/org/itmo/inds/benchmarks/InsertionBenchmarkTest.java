package org.itmo.inds.benchmarks;

import org.itmo.inds.oracle.Oracle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The measured operations of the single-point insertion benchmarks compute the right result (checked against the
 * oracle), on a steady-state and a generational snapshot. Both are ZDT1 snapshots, so that the 2D benchmark (two
 * objectives only) runs on them too.
 */
class InsertionBenchmarkTest {
    private static final String STEADY_STATE = "snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#5000";
    private static final String GENERATIONAL = "snapshots/generational/zdt/zdt1_gen10000_iter100_dataset1.json#50";

    static Stream<Arguments> algorithmsAndSnapshots() {
        return Arrays.stream(InsertionAlgorithm.values())
                .flatMap(algorithm -> Stream.of(STEADY_STATE, GENERATIONAL)
                        .map(snapshot -> Arguments.of(algorithm, snapshot)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("algorithmsAndSnapshots")
    void insertionResultIsCorrect(final InsertionAlgorithm algorithm, final String snapshot) {
        final InsertionBenchmark benchmark = new InsertionBenchmark();
        benchmark.algorithm = algorithm;
        benchmark.snapshot = snapshot;
        benchmark.prepare();
        benchmark.reset();
        assertEquals(expected(algorithm, populationWithAddend(snapshot)), benchmark.insert());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {STEADY_STATE, GENERATIONAL})
    void twoDimInsertionAddsThePoint(final String snapshot) {
        final TwoDimInsertionBenchmark benchmark = new TwoDimInsertionBenchmark();
        benchmark.snapshot = snapshot;
        benchmark.prepare();
        final Set<List<Double>> distinct = new HashSet<>();
        for (final double[] point : populationWithAddend(snapshot)) {
            distinct.add(List.of(point[0], point[1]));
        }
        benchmark.reset();
        assertEquals(distinct.size(), benchmark.insert(), "population size");
    }

    @Test
    void lexicographicOrderTreatsNegativeZeroAsZero() {
        final List<double[]> points = new ArrayList<>(List.of(new double[]{0.0, 1.0}, new double[]{-0.0, 2.0}));
        points.sort(Comparator.comparing(point -> point, BenchmarkInput::compareLexicographically));
        assertEquals(1.0, points.get(0)[1], "{0, 1} dominates {-0, 2} and comes first");
    }

    /**
     * @return the snapshot's population followed by its addend
     */
    private static double[][] populationWithAddend(final String snapshot) {
        final BenchmarkInput input = BenchmarkInput.snapshot(snapshot);
        final List<double[]> points = new ArrayList<>(input.population());
        points.add(input.addends().get(0));
        return points.toArray(new double[0][]);
    }

    /**
     * @return what the measured operation returns: the rank of the new point (the last one); for DC the rank of the
     * lexicographically first point; for DC1 / DC2 the size of the new population
     */
    private static int expected(final InsertionAlgorithm algorithm, final double[][] points) {
        final int[] oracleRanks = Oracle.ranks(points);
        return switch (algorithm) {
            case DC -> oracleRanks[lexicographicallyFirst(points)];
            case DC1, DC2 -> points.length;
            case LEVEL, ENLU -> oracleRanks[points.length - 1];
        };
    }

    private static int lexicographicallyFirst(final double[][] points) {
        int first = 0;
        for (int i = 1; i < points.length; ++i) {
            if (BenchmarkInput.compareLexicographically(points[i], points[first]) < 0) {
                first = i;
            }
        }
        return first;
    }
}
