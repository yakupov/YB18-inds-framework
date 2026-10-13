package org.itmo.inds.oracle;

import org.itmo.inds.population.CasJfbyPopulation;
import org.itmo.inds.population.CoarseLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulationRankHint;
import org.itmo.inds.population.JfbyPopulation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.itmo.inds.oracle.ReferencePopulation.Eviction.BATCH;
import static org.itmo.inds.oracle.ReferencePopulation.Eviction.ONE_WORST;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The populations of inds-core against the {@link ReferencePopulation} ({@link ModelCrossCheck}) on
 * {@link OracleInputs}, with an unlimited and with small expected sizes. ENLU: {@code EnluModelCrossCheckTest}
 * (inds-baselines).
 */
class ModelCrossCheckTest {
    private static final List<ModelCrossCheck.PopulationUnderTest> POPULATIONS = List.of(
            new ModelCrossCheck.PopulationUnderTest("Level", JfbyPopulation::new, ONE_WORST),
            new ModelCrossCheck.PopulationUnderTest("Coarse", CoarseLockJfbyPopulation::new, ONE_WORST),
            new ModelCrossCheck.PopulationUnderTest("Fine", FineLockJfbyPopulation::new, BATCH),
            new ModelCrossCheck.PopulationUnderTest("Fine-RankHint", FineLockJfbyPopulationRankHint::new, BATCH),
            new ModelCrossCheck.PopulationUnderTest("CAS",
                    expected -> new CasJfbyPopulation<>(cap(expected), false), ONE_WORST),
            new ModelCrossCheck.PopulationUnderTest("CAS-oneByOne",
                    expected -> new CasJfbyPopulation<>(cap(expected), true), ONE_WORST));

    /**
     * @return CAS takes an {@code int} expected size: larger values (unlimited) are capped
     */
    private static int cap(final long expected) {
        return (int) Math.min(expected, Integer.MAX_VALUE);
    }

    static Stream<Arguments> cases() {
        return POPULATIONS.stream().flatMap(population -> OracleInputs.cases().stream()
                .flatMap(inputCase -> ModelCrossCheck.expectedSizes(inputCase.points().length)
                        .mapToObj(expectedSize -> Arguments.of(population, inputCase, expectedSize))));
    }

    @ParameterizedTest(name = "{0} {1}, expected size {2}")
    @MethodSource("cases")
    void populationMatchesModel(final ModelCrossCheck.PopulationUnderTest population,
                                final OracleInputs.Case inputCase, final long expectedSize) {
        assertEquals(Optional.empty(), ModelCrossCheck.check(population, inputCase.points(), expectedSize));
    }
}
