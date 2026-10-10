package org.itmo.inds.baselines.enlu;

import org.itmo.inds.oracle.OracleInputs;
import org.itmo.inds.oracle.PopulationOracle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Oracle cross-check of the ENLU population with {@link PopulationOracle} on inds-core's {@link OracleInputs}.
 */
class EnluOracleCrossCheckTest {
    static Stream<OracleInputs.Case> cases() {
        return OracleInputs.cases().stream();
    }

    @ParameterizedTest(name = "EnluPopulation {0}")
    @MethodSource("cases")
    void populationMatchesOracle(final OracleInputs.Case inputCase) {
        assertEquals(Optional.empty(),
                PopulationOracle.check(() -> new EnluPopulation<>(Long.MAX_VALUE), inputCase.points()));
    }
}
