package org.itmo.inds.stress;

import org.itmo.inds.baselines.enlu.EnluPopulation;

import java.util.List;

/**
 * Concurrency stress test of the ENLU population (globally synchronized); harness: inds-core's
 * {@link AbstractConcurrentInsertionStressTest}.
 */
class EnluConcurrentInsertionStressTest extends AbstractConcurrentInsertionStressTest {
    @Override
    protected List<PopulationUnderTest> populations() {
        return List.of(new PopulationUnderTest("ENLU", () -> new EnluPopulation<>(Long.MAX_VALUE)));
    }
}
