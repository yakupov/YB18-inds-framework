package org.itmo.inds.benchmarks;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.population.CasJfbyPopulation;
import org.itmo.inds.population.CoarseLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulationRankHint;
import org.itmo.inds.population.JfbyNonDominationLevel;
import org.itmo.inds.population.JfbyPopulation;
import org.itmo.inds.sorting.IncrementalJfbSorter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The populations of {@link ConcurrentInsertionBenchmark} (thesis Chapter 4), all built from the same immutable
 * levels with an unlimited expected size (nothing is evicted).
 */
public enum ConcurrentAlgorithm {
    /**
     * Level, inserting all points from one thread: the sequential reference.
     */
    LEVEL(levels -> new JfbyPopulation<>(new ArrayList<>(levels), Long.MAX_VALUE)),
    /**
     * Coarse: Level behind a global lock.
     */
    COARSE(levels -> new CoarseLockJfbyPopulation<>(new ArrayList<>(levels), Long.MAX_VALUE)),
    /**
     * Fine as in the thesis.
     */
    FINE(levels -> new FineLockJfbyPopulation<>(Shared.SORTER, new CopyOnWriteArrayList<>(levels),
            Long.MAX_VALUE, Shared.FINE_DELETION_THRESHOLD)),
    /**
     * Fine, 2019 revision with the rank-hint retry.
     */
    FINE_RANK_HINT(levels -> new FineLockJfbyPopulationRankHint<>(Shared.SORTER,
            new CopyOnWriteArrayList<>(levels), Long.MAX_VALUE, Shared.FINE_DELETION_THRESHOLD)),
    /**
     * CAS, several addends of a level at once by full sorting.
     */
    CAS(levels -> new CasJfbyPopulation<>(references(levels), Integer.MAX_VALUE, false)),
    /**
     * CAS, one point at a time with time-stamping (the variant of the thesis experiments).
     */
    CAS_ONE_BY_ONE(levels -> new CasJfbyPopulation<>(references(levels), Integer.MAX_VALUE, true));

    /**
     * Constants of the constants' factories (an enum constant cannot refer to the enum's own static fields).
     */
    private static final class Shared {
        /**
         * The sorter inside the levels (DC2), as in the benchmarks of the cited code; stateless and shared.
         */
        private static final IncrementalJfbSorter SORTER = new IncrementalJfbSorter();
        /**
         * Batch eviction above 1.2 times the expected size: the default of the Fine classes (no eviction happens
         * here).
         */
        private static final double FINE_DELETION_THRESHOLD = 1.2;
    }

    private final Function<List<JfbyNonDominationLevel<Void>>, ManagedPopulation<Void>> factory;

    ConcurrentAlgorithm(final Function<List<JfbyNonDominationLevel<Void>>, ManagedPopulation<Void>> factory) {
        this.factory = factory;
    }

    /**
     * @param levels the levels (shared: they are immutable)
     * @return a new population holding them
     */
    ManagedPopulation<Void> create(final List<JfbyNonDominationLevel<Void>> levels) {
        return factory.apply(levels);
    }

    /**
     * @return one level per front, members as individuals without payload in lexicographic order
     */
    static List<JfbyNonDominationLevel<Void>> levels(final BenchmarkInput input) {
        final List<JfbyNonDominationLevel<Void>> levels = new ArrayList<>();
        for (final double[][] front : input.fronts()) {
            final List<Individual<Void>> members = new ArrayList<>();
            for (final double[] point : front) {
                members.add(new FitnessAndCdIndividual<>(point.clone(), null));
            }
            members.sort((first, second) ->
                    BenchmarkInput.compareLexicographically(first.getObjectives(), second.getObjectives()));
            levels.add(new JfbyNonDominationLevel<>(Shared.SORTER, members));
        }
        return levels;
    }

    private static CopyOnWriteArrayList<AtomicReference<JfbyNonDominationLevel<Void>>> references(
            final List<JfbyNonDominationLevel<Void>> levels) {
        final CopyOnWriteArrayList<AtomicReference<JfbyNonDominationLevel<Void>>> references =
                new CopyOnWriteArrayList<>();
        for (final JfbyNonDominationLevel<Void> level : levels) {
            references.add(new AtomicReference<>(level));
        }
        return references;
    }
}
