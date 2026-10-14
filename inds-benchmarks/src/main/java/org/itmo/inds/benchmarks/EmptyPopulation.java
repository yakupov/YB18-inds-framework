package org.itmo.inds.benchmarks;

import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.baselines.enlu.EnluPopulation;
import org.itmo.inds.twod.TwoDimTreapPopulation;

import java.util.function.Supplier;

/**
 * Populations without a size limit that start empty, for the benchmarks that fill them themselves.
 */
public enum EmptyPopulation {
    /**
     * 2D: levels stored in treaps (two objectives only).
     */
    TWO_DIM(() -> new TwoDimTreapPopulation<>(Long.MAX_VALUE)),
    /**
     * ENLU population.
     */
    ENLU(() -> new EnluPopulation<>(Long.MAX_VALUE));

    private final Supplier<ManagedPopulation<Void>> factory;

    EmptyPopulation(final Supplier<ManagedPopulation<Void>> factory) {
        this.factory = factory;
    }

    /**
     * @return a new empty population
     */
    ManagedPopulation<Void> create() {
        return factory.get();
    }
}
