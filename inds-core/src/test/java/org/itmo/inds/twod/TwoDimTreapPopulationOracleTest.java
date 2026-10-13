package org.itmo.inds.twod;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.RankedIndividual;
import org.itmo.inds.oracle.OracleInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The 2D treap population against {@link ReferenceTwoDimPopulation} on the inputs of {@link OracleInputs} with two
 * objectives, with points equal only to themselves (so points with equal coordinates are kept, in one bucket), without
 * and with eviction. Points equal by coordinates are covered by the shared {@code OracleCrossCheckTest}.
 */
class TwoDimTreapPopulationOracleTest {
    static Stream<Arguments> cases() {
        return OracleInputs.cases().stream()
                .filter(inputCase -> inputCase.points()[0].length == 2)
                .flatMap(inputCase -> LongStream.of(Long.MAX_VALUE, 1, 3, 10, inputCase.points().length / 3)
                        .filter(expectedSize -> expectedSize > 0)
                        .distinct()
                        .mapToObj(expectedSize -> Arguments.of(inputCase, expectedSize)));
    }

    /**
     * After every insertion: the rank {@code determineRank} computes before it and {@code addIndividual} returns,
     * {@code size()}, the snapshot levels (members, order, crowding distances), and {@code getRandomSolutions} for all
     * points (ranks and crowding distances computed from the treaps).
     */
    @ParameterizedTest(name = "TwoDimTreapPopulation {0}, expected size {1}")
    @MethodSource("cases")
    void matchesReference(final OracleInputs.Case inputCase, final long expectedSize) {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(expectedSize);
        final ReferenceTwoDimPopulation reference = new ReferenceTwoDimPopulation(expectedSize);
        final double[][] points = inputCase.points();
        for (int i = 0; i < points.length; ++i) {
            final int step = i + 1;
            final Individual<Object> point = new IdentityPoint(i, points[i]);
            final int expectedRank = reference.rankOf(point);
            assertEquals(expectedRank, population.determineRank(point), "determineRank before step " + step);
            assertEquals(expectedRank, population.addIndividual(point), "addIndividual at step " + step);
            reference.add(point);
            assertEquals(reference.size(), population.size(), "size() after step " + step);
            assertSameLevels(reference.levels(), population.getSnapshot().getLevels(), step);
            assertRandomSolutionsMatch(reference.levels(), population.getRandomSolutions(population.size()), step);
        }
    }

    private static void assertSameLevels(final List<List<Individual<Object>>> expected,
                                         final List<NonDominationLevel<Object>> actual, final int step) {
        assertEquals(expected.size(), actual.size(), "number of levels after step " + step);
        for (int rank = 0; rank < expected.size(); ++rank) {
            final List<Individual<Object>> members = actual.get(rank).getMembers();
            assertEquals(payloads(expected.get(rank)), payloads(members), "level " + rank + " after step " + step);
            final double[] crowdingDistances = ReferenceTwoDimPopulation.crowdingDistances(expected.get(rank));
            assertEquals(Arrays.stream(crowdingDistances).boxed().toList(),
                    members.stream().map(Individual::getCrowdingDistance).toList(),
                    "crowding distances of level " + rank + " after step " + step);
        }
    }

    private static void assertRandomSolutionsMatch(final List<List<Individual<Object>>> expected,
                                                   final List<RankedIndividual<Object>> solutions, final int step) {
        final Map<Object, RankedIndividual<Object>> byPayload = new HashMap<>();
        for (final RankedIndividual<Object> solution : solutions) {
            byPayload.put(solution.getPayload(), solution);
        }
        for (int rank = 0; rank < expected.size(); ++rank) {
            final double[] crowdingDistances = ReferenceTwoDimPopulation.crowdingDistances(expected.get(rank));
            for (int i = 0; i < expected.get(rank).size(); ++i) {
                final Object payload = expected.get(rank).get(i).getPayload();
                final RankedIndividual<Object> solution = byPayload.remove(payload);
                assertNotNull(solution, "point " + payload + " missing from the random solutions, step " + step);
                assertEquals(rank, solution.getRank(), "rank of point " + payload + ", step " + step);
                assertEquals(crowdingDistances[i], solution.getCrowdingDistance(),
                        "crowding distance of point " + payload + ", step " + step);
            }
        }
        assertEquals(Map.of(), byPayload, "random solutions that are not in the population, step " + step);
    }

    private static List<Object> payloads(final List<Individual<Object>> members) {
        return members.stream().map(Individual::getPayload).toList();
    }
}
