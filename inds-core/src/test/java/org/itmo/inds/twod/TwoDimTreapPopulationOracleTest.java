package org.itmo.inds.twod;

import org.itmo.inds.oracle.Oracle;
import org.itmo.inds.oracle.OracleInputs;
import org.itmo.inds.oracle.PopulationOracle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Oracle cross-checks of the 2D treap population on the inputs of {@link OracleInputs} with two objectives: before
 * each insertion, the rank {@code determineRank} computes for the new point ({@code addPoint} returns nothing); at the
 * {@link PopulationOracle#isCheckpoint checkpoints}, the level of every point so far; at the end,
 * {@code detRankOfExPoint} of every point. The population keeps one copy of
 * equal points (a set of {@link TwoDimPoint}s, equal by coordinates); equal points have equal oracle ranks.
 */
class TwoDimTreapPopulationOracleTest {
    static Stream<OracleInputs.Case> cases() {
        return OracleInputs.cases().stream().filter(inputCase -> inputCase.points()[0].length == 2);
    }

    private static TwoDimPoint point(final double[] objectives) {
        return new TwoDimPoint(objectives[0], objectives[1]);
    }

    @ParameterizedTest(name = "TwoDimTreapPopulation {0}")
    @MethodSource("cases")
    void levelsMatchOracle(final OracleInputs.Case inputCase) {
        final double[][] points = inputCase.points();
        final int n = points.length;
        final List<TwoDimTreap> levels = new ArrayList<>();
        final TwoDimTreapPopulation population = new TwoDimTreapPopulation(new HashSet<>(), levels);
        final Set<TwoDimPoint> distinct = new HashSet<>();
        for (int i = 0; i < n; ++i) {
            final int step = i + 1;
            final int[] oracleRanks = Oracle.ranks(Arrays.copyOf(points, step));
            final TwoDimPoint added = point(points[i]);
            assertEquals(oracleRanks[i], population.determineRank(added), "rank of the new point at step " + step);
            population.addPoint(added);
            distinct.add(point(points[i]));
            if (PopulationOracle.isCheckpoint(step, n)) {
                assertLevelsMatch(levels, distinct.size(), points, oracleRanks, step);
                assertEquals(distinct.size(), population.size(), "size() after step " + step);
            }
        }
        // detRankOfExPoint (rank of a point already in the population, used by getRandWithRank) at the end
        final int[] oracleRanks = Oracle.ranks(points);
        for (int j = 0; j < n; ++j) {
            assertEquals(oracleRanks[j], population.detRankOfExPoint(point(points[j])),
                    "detRankOfExPoint of point " + j);
        }
    }

    /**
     * Asserts that the levels hold {@code distinctCount} points, each once, and every point inserted so far in the
     * level of its oracle rank.
     */
    private static void assertLevelsMatch(final List<TwoDimTreap> levels, final int distinctCount,
                                          final double[][] points, final int[] oracleRanks, final int step) {
        final Map<TwoDimPoint, Integer> levelOf = new HashMap<>();
        for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
            for (final TwoDimPoint member : TwoDimTreapTraversal.inOrderPoints(levels.get(levelIndex))) {
                assertNull(levelOf.put(member, levelIndex), "point in two levels, step " + step);
            }
        }
        assertEquals(distinctCount, levelOf.size(), "points in levels after step " + step);
        for (int j = 0; j < step; ++j) {
            assertEquals(oracleRanks[j], levelOf.get(point(points[j])), "level of point " + j + " after step " + step);
        }
    }
}
