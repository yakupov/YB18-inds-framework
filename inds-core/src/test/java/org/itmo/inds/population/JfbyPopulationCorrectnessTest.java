package org.itmo.inds.population;

import org.itmo.inds.api.ManagedPopulation;

public class JfbyPopulationCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected ManagedPopulation<Object> constructPopulation(int dimensionsCount) {
        return new JfbyPopulation<>(Long.MAX_VALUE);
    }
}
