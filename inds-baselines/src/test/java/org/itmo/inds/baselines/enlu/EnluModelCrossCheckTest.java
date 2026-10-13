package org.itmo.inds.baselines.enlu;

import org.itmo.inds.oracle.ModelCrossCheck;
import org.itmo.inds.oracle.OracleInputs;
import org.itmo.inds.oracle.PopulationOracle;
import org.itmo.inds.oracle.ReferencePopulation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ENLU population against inds-core's {@link ReferencePopulation} ({@link ModelCrossCheck}) on its
 * {@link OracleInputs}, with an unlimited and with small expected sizes; and its {@code clone()}
 * ({@link PopulationOracle#checkClone}).
 */
class EnluModelCrossCheckTest {
    private static final ModelCrossCheck.PopulationUnderTest ENLU = new ModelCrossCheck.PopulationUnderTest(
            "EnluPopulation", EnluPopulation::new, ReferencePopulation.Eviction.ONE_WORST);

    static Stream<Arguments> cases() {
        return OracleInputs.cases().stream().flatMap(inputCase -> ModelCrossCheck
                .expectedSizes(inputCase.points().length)
                .mapToObj(expectedSize -> Arguments.of(inputCase, expectedSize)));
    }

    @ParameterizedTest(name = "EnluPopulation {0}, expected size {1}")
    @MethodSource("cases")
    void populationMatchesModel(final OracleInputs.Case inputCase, final long expectedSize) {
        assertEquals(Optional.empty(), ModelCrossCheck.check(ENLU, inputCase.points(), expectedSize));
    }

    static Stream<OracleInputs.Case> cloneCases() {
        return OracleInputs.cases().stream().filter(inputCase -> inputCase.points().length >= 3);
    }

    @ParameterizedTest(name = "EnluPopulation {0}")
    @MethodSource("cloneCases")
    void cloneAndOriginalEvolveIndependently(final OracleInputs.Case inputCase) {
        assertEquals(Optional.empty(),
                PopulationOracle.checkClone(() -> new EnluPopulation<>(Long.MAX_VALUE), inputCase.points()));
    }
}
