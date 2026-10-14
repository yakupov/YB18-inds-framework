package org.itmo.inds.stress;

import org.itmo.inds.population.CasJfbyPopulation;
import org.itmo.inds.population.CoarseLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulationRankHint;

import java.util.List;

/**
 * Concurrency stress test of the asynchronous populations of inds-core (Coarse, Fine in both versions, CAS in both
 * modes); harness: {@link AbstractConcurrentInsertionStressTest}.
 */
class ConcurrentInsertionStressTest extends AbstractConcurrentInsertionStressTest {
    /**
     * Fine removes points in batches once the population exceeds this multiple of its expected size.
     */
    private static final double FINE_DELETION_THRESHOLD = 1.2;

    @Override
    protected List<PopulationUnderTest> populations() {
        return List.of(
                new PopulationUnderTest("Coarse", () -> new CoarseLockJfbyPopulation<>(Long.MAX_VALUE),
                        CoarseLockJfbyPopulation::new, 1),
                new PopulationUnderTest("Fine-RankHint", () -> new FineLockJfbyPopulationRankHint<>(Long.MAX_VALUE),
                        expectedSize -> new FineLockJfbyPopulationRankHint<>(expectedSize, FINE_DELETION_THRESHOLD),
                        FINE_DELETION_THRESHOLD),
                new PopulationUnderTest("Fine-thesis", () -> new FineLockJfbyPopulation<>(Long.MAX_VALUE),
                        expectedSize -> new FineLockJfbyPopulation<>(expectedSize, FINE_DELETION_THRESHOLD),
                        FINE_DELETION_THRESHOLD),
                new PopulationUnderTest("CAS", () -> new CasJfbyPopulation<>(Integer.MAX_VALUE, false),
                        expectedSize -> new CasJfbyPopulation<>((int) expectedSize, false), 1),
                new PopulationUnderTest("CAS-oneByOne", () -> new CasJfbyPopulation<>(Integer.MAX_VALUE, true),
                        expectedSize -> new CasJfbyPopulation<>((int) expectedSize, true), 1));
    }
}
