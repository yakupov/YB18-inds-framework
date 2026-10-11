package org.itmo.inds.benchmarks;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.oracle.Oracle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * After the measured operation of the concurrent benchmark, the population holds the initial points and the whole
 * sequence, each distinct point once, in the level of its true rank.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentInsertionBenchmarkTest {
    private static final String SEQUENCE = "sequences/dtlz3d/dtlz1_dim3_initSize5000_addendsCount1000_dataset1.json";
    private static final int THREADS = 6;

    /**
     * Oracle rank of every distinct point of population and sequence, by objective vector.
     */
    private Map<List<Double>, Integer> expectedRanks;

    @BeforeAll
    void computeExpectedRanks() {
        final BenchmarkInput input = BenchmarkInput.sequence(SEQUENCE);
        final List<double[]> points = new ArrayList<>(input.population());
        points.addAll(input.addends());
        final double[][] all = points.toArray(new double[0][]);
        final int[] ranks = Oracle.ranks(all);
        expectedRanks = new HashMap<>();
        for (int i = 0; i < all.length; ++i) {
            expectedRanks.put(objectives(all[i]), ranks[i]);
        }
    }

    @ParameterizedTest
    @EnumSource(ConcurrentAlgorithm.class)
    void allPointsEndUpInTheirLevels(final ConcurrentAlgorithm algorithm) {
        final ConcurrentInsertionBenchmark benchmark = new ConcurrentInsertionBenchmark();
        benchmark.algorithm = algorithm;
        benchmark.sequence = SEQUENCE;
        benchmark.threads = THREADS;
        benchmark.prepare();
        benchmark.reset();
        benchmark.insertAll();
        assertLevelsMatchOracle(benchmark.population().getSnapshot().getLevels());
    }

    private void assertLevelsMatchOracle(final List<NonDominationLevel<Void>> levels) {
        final Map<List<Double>, Integer> levelOf = new HashMap<>();
        for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
            for (final Individual<Void> member : levels.get(levelIndex).getMembers()) {
                assertNull(levelOf.put(objectives(member.getObjectives()), levelIndex),
                        "point present twice");
            }
        }
        assertEquals(expectedRanks, levelOf, "level of every point");
    }

    private static List<Double> objectives(final double[] point) {
        return Arrays.stream(point).boxed().toList();
    }
}
