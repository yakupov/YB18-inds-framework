package org.itmo.inds.oracle;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.population.CasJfbyPopulation;
import org.itmo.inds.population.CoarseLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulationRankHint;
import org.itmo.inds.population.JfbyPopulation;
import org.itmo.inds.sorting.IncrementalJfbSorter;
import org.itmo.inds.sorting.JfbSorter;
import org.itmo.inds.util.RankedPopulation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Oracle cross-checks of the core algorithms against M. Buzdalov's independent sorter ({@link Oracle}),
 * single-threaded, on {@link OracleInputs}: DC (full sort), DC1 / DC2 (incremental insertion), and every population.
 * Unlike the golden-master tests, these check correctness, not sameness with the cited code.
 */
class OracleCrossCheckTest {
    private record PopulationUnderTest(String name, Supplier<ManagedPopulation<Integer>> factory) {
        @Override
        @Nonnull
        public String toString() {
            return name;
        }
    }

    static Stream<OracleInputs.Case> cases() {
        return OracleInputs.cases().stream();
    }

    static Stream<Arguments> populationCases() {
        final List<PopulationUnderTest> populations = List.of(
                new PopulationUnderTest("Level", () -> new JfbyPopulation<>(Long.MAX_VALUE)),
                new PopulationUnderTest("Coarse", () -> new CoarseLockJfbyPopulation<>(Long.MAX_VALUE)),
                new PopulationUnderTest("Fine", () -> new FineLockJfbyPopulation<>(Long.MAX_VALUE)),
                new PopulationUnderTest("Fine-RankHint", () -> new FineLockJfbyPopulationRankHint<>(Long.MAX_VALUE)),
                new PopulationUnderTest("CAS", () -> new CasJfbyPopulation<>(Integer.MAX_VALUE, false)),
                new PopulationUnderTest("CAS-oneByOne", () -> new CasJfbyPopulation<>(Integer.MAX_VALUE, true)));
        return populations.stream()
                .flatMap(population -> cases().map(inputCase -> Arguments.of(population, inputCase)));
    }

    /**
     * @return the points as individuals (copies of the arrays) whose payloads are their indices
     */
    @SuppressWarnings("unchecked")
    private static Individual<Integer>[] individuals(final double[][] points) {
        final Individual<Integer>[] individuals = new Individual[points.length];
        for (int i = 0; i < points.length; ++i) {
            individuals[i] = new FitnessAndCdIndividual<>(points[i].clone(), i);
        }
        return individuals;
    }

    /**
     * @return oracle ranks in the order of {@code individuals} (whose payloads index {@code points})
     */
    private static int[] oracleRanks(final Object[] individuals, final double[][] points) {
        final int[] ranksByIndex = Oracle.ranks(points);
        final int[] ranks = new int[individuals.length];
        for (int i = 0; i < individuals.length; ++i) {
            @SuppressWarnings("unchecked") final Individual<Integer> individual = (Individual<Integer>) individuals[i];
            ranks[i] = ranksByIndex[individual.getPayload()];
        }
        return ranks;
    }

    @ParameterizedTest(name = "DC {0}")
    @MethodSource("cases")
    void dcFullSortMatchesOracle(final OracleInputs.Case inputCase) {
        final Individual<Integer>[] population = individuals(inputCase.points());
        final int[] ranks = new JfbSorter().performNds(population); // sorts population lexicographically
        assertArrayEquals(oracleRanks(population, inputCase.points()), ranks);
    }

    @ParameterizedTest(name = "DC1 {0}")
    @MethodSource("cases")
    void dc1IncrementalMatchesOracle(final OracleInputs.Case inputCase) {
        assertIncrementalMatchesOracle(new JfbSorter(), inputCase.points());
    }

    @ParameterizedTest(name = "DC2 {0}")
    @MethodSource("cases")
    void dc2IncrementalMatchesOracle(final OracleInputs.Case inputCase) {
        assertIncrementalMatchesOracle(new IncrementalJfbSorter(), inputCase.points());
    }

    /**
     * Inserts one point at a time into the ranked, lexicographically sorted population, starting from the first point,
     * and compares the ranks with the oracle at the {@link PopulationOracle#isCheckpoint checkpoints}.
     */
    @SuppressWarnings("rawtypes")
    private static void assertIncrementalMatchesOracle(final JfbSorter sorter, final double[][] points) {
        final int n = points.length;
        final Individual<Integer>[] all = individuals(points);
        Individual[] population = Arrays.copyOf(all, 1, Individual[].class);
        int[] ranks = new JfbSorter().performNds(population);
        for (int i = 1; i < n; ++i) {
            final RankedPopulation<Individual> ranked = sorter.performIncrementalNds(population, ranks, all[i]);
            population = ranked.getPop();
            ranks = ranked.getRanks();
            final int step = i + 1;
            if (PopulationOracle.isCheckpoint(step, n)) {
                assertEquals(step, population.length, "population size after step " + step);
                assertArrayEquals(oracleRanks(population, Arrays.copyOf(points, step)), ranks,
                        "ranks after step " + step);
            }
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("populationCases")
    void populationMatchesOracle(final PopulationUnderTest population, final OracleInputs.Case inputCase) {
        assertEquals(Optional.empty(), PopulationOracle.check(population.factory(), inputCase.points()));
    }
}
