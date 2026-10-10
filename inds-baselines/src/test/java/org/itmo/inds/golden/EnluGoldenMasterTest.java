package org.itmo.inds.golden;

import org.itmo.inds.baselines.enlu.EnluPopulation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * Golden-master test of the ENLU population ({@link Target#ENLU}), with inds-core's golden infrastructure (from its
 * test-jar; terms and scenarios: package documentation in inds-core).
 */
class EnluGoldenMasterTest {
    @TestFactory
    Stream<DynamicTest> replayMatchesLegacyRecords() {
        return Stream.of(GoldenCheck.test(Target.ENLU, Replays.population(EnluPopulation::new)));
    }
}
