package org.itmo.inds.benchmarks;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.population.JfbyNonDominationLevel;
import org.itmo.inds.sorting.IncrementalJfbSorter;
import org.itmo.inds.sorting.JfbSorter;
import org.itmo.inds.util.RankedPopulation;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * The algorithms of {@link InsertionBenchmark} (thesis Chapter 3): each inserts the snapshot's next point into the
 * snapshot's population. The population is rebuilt before every measured insertion (not measured).
 */
public enum InsertionAlgorithm {
    /**
     * DC: full non-dominated sorting of the population plus the new point.
     */
    DC(InsertionAlgorithm::fullSort),
    /**
     * DC1: incremental insertion into the ranked, lexicographically sorted population.
     */
    DC1(input -> incremental(input, new JfbSorter())),
    /**
     * DC2: DC1 with linear-time sweeps.
     */
    DC2(input -> incremental(input, new IncrementalJfbSorter())),
    /**
     * Level: insertion into the levels of the population.
     */
    LEVEL(input -> {
        final List<JfbyNonDominationLevel<Void>> levels = ConcurrentAlgorithm.levels(input);
        return populationInsertion(() -> ConcurrentAlgorithm.LEVEL.create(levels), input);
    }),
    /**
     * ENLU population (the Chapter 4 baseline): built once by inserting the points, copied before every insertion.
     */
    ENLU(InsertionAlgorithm::enluPopulation);

    private final Function<BenchmarkInput, PreparedInsertion> preparation;

    InsertionAlgorithm(final Function<BenchmarkInput, PreparedInsertion> preparation) {
        this.preparation = preparation;
    }

    /**
     * @param input a snapshot: population and the one point to insert
     * @return the prepared insertion of the input's first addend
     */
    PreparedInsertion prepare(final BenchmarkInput input) {
        return preparation.apply(input);
    }

    private static Individual<Void> individual(final double[] point) {
        return new FitnessAndCdIndividual<>(point.clone(), null);
    }

    /**
     * @return the population as individuals, in front order
     */
    @SuppressWarnings("unchecked")
    private static Individual<Void>[] individuals(final BenchmarkInput input) {
        return input.population().stream().map(InsertionAlgorithm::individual).toArray(Individual[]::new);
    }

    /**
     * DC: copies the lexicographically sorted population, appends the point and sorts everything. Returns the rank of
     * the first point in the result.
     */
    private static PreparedInsertion fullSort(final BenchmarkInput input) {
        final JfbSorter sorter = new JfbSorter();
        final Individual<Void>[] sorted = individuals(input);
        sorter.performNds(sorted); // sorts its argument lexicographically
        final Individual<Void> addend = individual(input.addends().get(0));
        return new PreparedInsertion() {
            @Override
            public void reset() {
            }

            @Override
            public int run() {
                final Individual<Void>[] extended = Arrays.copyOf(sorted, sorted.length + 1);
                extended[extended.length - 1] = addend;
                return sorter.performNds(extended)[0];
            }
        };
    }

    /**
     * DC1 / DC2: inserts the point into the ranked, lexicographically sorted population (the sorter returns a new
     * population and does not modify its inputs). Returns the size of the result.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PreparedInsertion incremental(final BenchmarkInput input, final JfbSorter sorter) {
        final Individual[] sorted = individuals(input);
        final int[] ranks = new JfbSorter().performNds(sorted); // sorts its argument; ranks in that order
        final Individual<Void> addend = individual(input.addends().get(0));
        return new PreparedInsertion() {
            private Individual[] population;
            private int[] populationRanks;

            @Override
            public void reset() {
                population = sorted.clone();
                populationRanks = ranks.clone();
            }

            @Override
            public int run() {
                final RankedPopulation<Individual> result =
                        sorter.performIncrementalNds(population, populationRanks, addend);
                return result.getRanks().length;
            }
        };
    }

    private interface PopulationFactory {
        ManagedPopulation<Void> create();
    }

    /**
     * Creates a population before every insertion; returns the rank of the inserted point.
     */
    private static PreparedInsertion populationInsertion(final PopulationFactory factory,
                                                         final BenchmarkInput input) {
        final Individual<Void> addend = individual(input.addends().get(0));
        return new PreparedInsertion() {
            private ManagedPopulation<Void> population;

            @Override
            public void reset() {
                population = factory.create();
            }

            @Override
            public int run() {
                return population.addIndividual(addend);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static PreparedInsertion enluPopulation(final BenchmarkInput input) {
        final ManagedPopulation<Void> template = EmptyPopulation.ENLU.create();
        for (final Individual<Void> individual : individuals(input)) {
            template.addIndividual(individual);
        }
        return populationInsertion(() -> (ManagedPopulation<Void>) template.clone(), input);
    }
}
