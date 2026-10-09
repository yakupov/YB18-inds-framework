package org.itmo.inds.population;

import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.baselines.enlu.EnluPopulation;

public class EnluPopulationCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected ManagedPopulation<Object> constructPopulation(int dimensionsCount) {
        return new EnluPopulation<>(Long.MAX_VALUE);
    }
}
