package org.itmo.inds.sorting;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.util.RankedPopulation;

/**
 * The {@code double[]} API of nds2016's {@code PPSN2014<double[]>} / {@code IncrementalPPSN<double[]>} on top of the
 * ported sorters, so that the nds2016 sorter tests run with their bodies unchanged ({@link JfbSorterNds2016Test},
 * {@link IncrementalJfbSorterNds2016Test}). Points are wrapped without copying: the returned populations hold the
 * caller's arrays.
 */
final class LegacySorterShim {
    private final JfbSorter fullSorter = new JfbSorter();
    private final JfbSorter incrementalSorter;

    private LegacySorterShim(JfbSorter incrementalSorter) {
        this.incrementalSorter = incrementalSorter;
    }

    /**
     * {@code PPSN2014}: DC (full sort) and DC1 (incremental insertion).
     */
    static LegacySorterShim dc1() {
        return new LegacySorterShim(new JfbSorter());
    }

    /**
     * {@code IncrementalPPSN}: DC2 (incremental insertion with linear sweeps).
     */
    static LegacySorterShim dc2() {
        return new LegacySorterShim(new IncrementalJfbSorter());
    }

    private static Individual<Void>[] wrap(double[][] pop) {
        @SuppressWarnings("unchecked") final Individual<Void>[] individuals = new Individual[pop.length];
        for (int i = 0; i < pop.length; ++i) {
            individuals[i] = new FitnessAndCdIndividual<>(pop[i], null);
        }
        return individuals;
    }

    /**
     * As {@code PPSN2014#performNds}: sorts {@code pop} lexicographically in place, returns the ranks in that order.
     */
    int[] performNds(double[][] pop) {
        final Individual<Void>[] individuals = wrap(pop);
        final int[] ranks = fullSorter.performNds(individuals);
        for (int i = 0; i < pop.length; ++i) {
            pop[i] = individuals[i].getObjectives();
        }
        return ranks;
    }

    /**
     * As {@code PPSN2014#performIncrementalNds}: {@code pop} lexicographically sorted, {@code ranks} aligned.
     */
    @SuppressWarnings("rawtypes")
    RankedPopulation<double[]> performIncrementalNds(double[][] pop, int[] ranks, double[] addend) {
        final RankedPopulation<Individual> ranked = incrementalSorter.performIncrementalNds(wrap(pop), ranks,
                new FitnessAndCdIndividual<>(addend, null));
        final double[][] points = new double[ranked.getPop().length][];
        for (int i = 0; i < points.length; ++i) {
            points[i] = ranked.getPop()[i].getObjectives();
        }
        return new RankedPopulation<>(points, ranked.getRanks());
    }
}
