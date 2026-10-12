package org.itmo.inds.twod;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.oracle.Oracle;
import org.itmo.inds.oracle.OracleInputs;
import org.itmo.inds.oracle.PopulationOracle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
 * {@link PopulationOracle#isCheckpoint checkpoints}, the level of every point so far and {@code size()}; at the end,
 * {@code detRankOfExPoint} of every point. Each input runs twice ({@link PointEquality}): with points equal by
 * coordinates (the population keeps one of equal points) and with points equal only to themselves (the population
 * keeps all of them, points with equal coordinates in one bucket). Points with equal coordinates have equal oracle
 * ranks.
 */
class TwoDimTreapPopulationOracleTest {
    /**
     * How the input points are made into {@link Individual}s, which decides which of them the population keeps.
     */
    enum PointEquality {
        /** {@link FitnessAndCdIndividual}: equal iff the coordinates are; one of equal points is kept. */
        COORDINATES {
            @Override
            Individual<Object> point(final double[] objectives) {
                return new FitnessAndCdIndividual<>(objectives, null);
            }
        },
        /** Equal only to itself: every point is kept, points with equal coordinates share a bucket. */
        IDENTITY {
            @Override
            Individual<Object> point(final double[] objectives) {
                return new IdentityPoint(objectives);
            }
        };

        abstract Individual<Object> point(double[] objectives);
    }

    static Stream<Arguments> cases() {
        return OracleInputs.cases().stream()
                .filter(inputCase -> inputCase.points()[0].length == 2)
                .flatMap(inputCase -> Arrays.stream(PointEquality.values())
                        .map(equality -> Arguments.of(inputCase, equality)));
    }

    @ParameterizedTest(name = "TwoDimTreapPopulation {0}, points equal by {1}")
    @MethodSource("cases")
    void levelsMatchOracle(final OracleInputs.Case inputCase, final PointEquality equality) {
        final double[][] points = inputCase.points();
        final int n = points.length;
        final List<Individual<Object>> added = new ArrayList<>();
        final List<TwoDimTreap<Object>> levels = new ArrayList<>();
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(levels);
        final Set<Individual<Object>> kept = new HashSet<>();
        for (int i = 0; i < n; ++i) {
            final int step = i + 1;
            final int[] oracleRanks = Oracle.ranks(Arrays.copyOf(points, step));
            final Individual<Object> point = equality.point(points[i]);
            assertEquals(oracleRanks[i], population.determineRank(new TwoDimBucket<>(point)),
                    "rank of the new point at step " + step);
            population.addPoint(point);
            added.add(point);
            kept.add(point);
            if (PopulationOracle.isCheckpoint(step, n)) {
                assertLevelsMatch(levels, kept.size(), added, oracleRanks, step);
                assertEquals(kept.size(), population.size(), "size() after step " + step);
            }
        }
        // detRankOfExPoint (rank of a point already in the population, used by getRandWithRank) at the end
        final int[] oracleRanks = Oracle.ranks(points);
        for (int j = 0; j < n; ++j) {
            assertEquals(oracleRanks[j], population.detRankOfExPoint(added.get(j)), "detRankOfExPoint of point " + j);
        }
    }

    /**
     * Asserts that the levels hold {@code keptCount} points, each once, and every point added so far (or the kept point
     * equal to it) in the level of its oracle rank.
     */
    private static void assertLevelsMatch(final List<TwoDimTreap<Object>> levels, final int keptCount,
                                          final List<Individual<Object>> added, final int[] oracleRanks,
                                          final int step) {
        final Map<Individual<Object>, Integer> levelOf = new HashMap<>();
        for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
            for (final Individual<Object> member : TwoDimTreapTraversal.inOrderPoints(levels.get(levelIndex))) {
                assertNull(levelOf.put(member, levelIndex), "point in two levels, step " + step);
            }
        }
        assertEquals(keptCount, levelOf.size(), "points in levels after step " + step);
        for (int j = 0; j < step; ++j) {
            assertEquals(oracleRanks[j], levelOf.get(added.get(j)), "level of point " + j + " after step " + step);
        }
    }
}
