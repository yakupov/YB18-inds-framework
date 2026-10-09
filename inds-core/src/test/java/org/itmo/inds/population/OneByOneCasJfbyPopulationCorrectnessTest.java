package org.itmo.inds.population;

public class OneByOneCasJfbyPopulationCorrectnessTest extends AbstractCasJfbyPopulationCorrectnessTest {
    @Override
    protected CasJfbyPopulation<Object> constructPopulation(int dimensionsCount) {
        return new CasJfbyPopulation<>(100500, true);
    }
}
