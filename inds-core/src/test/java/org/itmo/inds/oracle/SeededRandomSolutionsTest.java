package org.itmo.inds.oracle;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.RankedIndividual;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code getRandomSolutions(count, random)} of every population of inds-core, single-threaded, on
 * {@link OracleInputs}: equal seeds give equal results, and a draw holds distinct members.
 */
class SeededRandomSolutionsTest {
    private static final long SEED = 20261019L;

    static Stream<Arguments> cases() {
        return OracleCrossCheckTest.casesOf(OracleCrossCheckTest.allPopulations());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cases")
    void equalSeedsGiveEqualSolutions(final OracleCrossCheckTest.PopulationUnderTest population,
                                      final OracleInputs.Case inputCase) {
        final ManagedPopulation<Integer> filled = filled(population, inputCase.points());
        final int count = Math.max(1, filled.size() / 2);
        assertEquals(payloads(filled.getRandomSolutions(count, new SplittableRandom(SEED))),
                payloads(filled.getRandomSolutions(count, new SplittableRandom(SEED))),
                "payloads of two draws of " + count + " with seed " + SEED);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cases")
    void drawHoldsDistinctMembers(final OracleCrossCheckTest.PopulationUnderTest population,
                                  final OracleInputs.Case inputCase) {
        final ManagedPopulation<Integer> filled = filled(population, inputCase.points());
        final List<Integer> drawn = payloads(filled.getRandomSolutions(filled.size() + 1, new SplittableRandom(SEED)));
        assertEquals(filled.size(), drawn.size(), "number of solutions drawn when asking for size() + 1");
        assertEquals(filled.size(), new HashSet<>(drawn).size(), "number of distinct payloads drawn: " + drawn);
    }

    private static ManagedPopulation<Integer> filled(final OracleCrossCheckTest.PopulationUnderTest population,
                                                     final double[][] points) {
        final ManagedPopulation<Integer> filled = population.factory().get();
        for (final Individual<Integer> individual : OracleCrossCheckTest.individuals(points)) {
            filled.addIndividual(individual);
        }
        return filled;
    }

    private static List<Integer> payloads(final List<RankedIndividual<Integer>> solutions) {
        return solutions.stream().map(RankedIndividual::getPayload).toList();
    }
}
