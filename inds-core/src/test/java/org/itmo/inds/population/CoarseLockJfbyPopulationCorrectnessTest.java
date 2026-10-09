package org.itmo.inds.population;

import org.itmo.inds.api.ManagedPopulation;

public class CoarseLockJfbyPopulationCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected ManagedPopulation<Object> constructPopulation(int dimensionsCount) {
        return new CoarseLockJfbyPopulation<>(Long.MAX_VALUE);
    }
}
