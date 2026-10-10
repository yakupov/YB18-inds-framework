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
    @Override
    protected List<PopulationUnderTest> populations() {
        return List.of(
                new PopulationUnderTest("Coarse", () -> new CoarseLockJfbyPopulation<>(Long.MAX_VALUE),
                        SizeCheck.AT_MOST),
                new PopulationUnderTest("Fine-RankHint", () -> new FineLockJfbyPopulationRankHint<>(Long.MAX_VALUE),
                        SizeCheck.EXACT),
                new PopulationUnderTest("Fine-thesis", () -> new FineLockJfbyPopulation<>(Long.MAX_VALUE),
                        SizeCheck.EXACT),
                new PopulationUnderTest("CAS", () -> new CasJfbyPopulation<>(Integer.MAX_VALUE, false),
                        SizeCheck.EXACT),
                new PopulationUnderTest("CAS-oneByOne", () -> new CasJfbyPopulation<>(Integer.MAX_VALUE, true),
                        SizeCheck.EXACT));
    }
}
