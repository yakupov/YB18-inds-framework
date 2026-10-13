package org.itmo.inds.benchmarks;

import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.baselines.enlu.EnluPopulation;
import org.itmo.inds.twod.TwoDimTreapPopulation;

import java.util.function.Supplier;

/**
 * The algorithms of {@link TwoDimSequenceBenchmark} (thesis Chapter 2): each inserts points one at a time into an
 * empty population.
 */
public enum TwoDimSequenceAlgorithm {
    /**
     * 2D: levels stored in treaps.
     */
    TWO_DIM(() -> new TwoDimTreapPopulation<>(Long.MAX_VALUE)),
    /**
     * ENLU population.
     */
    ENLU(() -> new EnluPopulation<>(Long.MAX_VALUE));

    private final Supplier<ManagedPopulation<Void>> factory;

    TwoDimSequenceAlgorithm(final Supplier<ManagedPopulation<Void>> factory) {
        this.factory = factory;
    }

    /**
     * @return a new empty population without a size limit
     */
    ManagedPopulation<Void> createPopulation() {
        return factory.get();
    }
}
