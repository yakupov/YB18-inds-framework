package org.itmo.inds.benchmarks;

import org.itmo.inds.testdata.SyntheticProblem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke test: every algorithm holds, after inserting a synthetic problem, exactly its distinct points.
 */
class TwoDimSequenceBenchmarkTest {
    private static final int SIZE = 250;

    static Stream<Arguments> algorithmsAndProblems() {
        return Arrays.stream(TwoDimSequenceAlgorithm.values())
                .flatMap(algorithm -> Arrays.stream(SyntheticProblem.values())
                        .map(problem -> Arguments.of(algorithm, problem)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("algorithmsAndProblems")
    void populationHoldsTheDistinctPoints(final TwoDimSequenceAlgorithm algorithm, final SyntheticProblem problem) {
        final TwoDimSequenceBenchmark benchmark = new TwoDimSequenceBenchmark();
        benchmark.algorithm = algorithm;
        benchmark.problem = problem;
        benchmark.size = SIZE;
        benchmark.prepare();
        for (int seed = 0; seed < TwoDimSequenceBenchmark.SEEDS; ++seed) { // invocation i uses the points of seed i
            final long distinct = Arrays.stream(problem.points(SIZE, seed))
                    .map(Arrays::toString).distinct().count();
            benchmark.reset();
            assertEquals(distinct, benchmark.insertAll(), "seed " + seed);
        }
    }
}
