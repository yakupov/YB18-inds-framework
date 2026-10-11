package org.itmo.inds.benchmarks;

import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.population.CasJfbyPopulation;
import org.itmo.inds.population.CoarseLockJfbyPopulation;
import org.itmo.inds.population.JfbyNonDominationLevel;
import org.itmo.inds.population.JfbyPopulation;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Thesis Chapter 4: the time to insert a whole sequence of points (1 001 offspring of a steady-state NSGA-II run)
 * into its initial population (5 000 points) with {@code threads} threads, the points dealt round-robin to the
 * threads. Includes creating the thread pool, as in the measurements of the cited code ({@link
 * ConcurrentInsertionDriver}). {@link ConcurrentAlgorithm#LEVEL} inserts from one thread whatever {@code threads}
 * says. Default sequence: DTLZ1 with 3 objectives; any sequence of {@code inds-benchmark-data} can be given with
 * {@code -p sequence=path}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 12, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(3)
public class ConcurrentInsertionBenchmark {
    @Param
    public ConcurrentAlgorithm algorithm;

    @Param("sequences/dtlz3d/dtlz1_dim3_initSize5000_addendsCount1000_dataset1.json")
    public String sequence;

    @Param({"3", "6", "12"})
    public int threads;

    private List<JfbyNonDominationLevel<Void>> levels;
    private List<double[]> addends;
    private Map<Integer, List<double[]>> addendsByThread;
    private Driver driver;
    private ManagedPopulation<Void> population;

    @Setup(Level.Trial)
    public void prepare() {
        final BenchmarkInput input = BenchmarkInput.sequence(sequence);
        levels = ConcurrentAlgorithm.levels(input);
        addends = input.addends();
        addendsByThread = new HashMap<>();
        for (int i = 0; i < addends.size(); ++i) {
            addendsByThread.computeIfAbsent(i % threads, thread -> new ArrayList<>()).add(addends.get(i));
        }
        driver = new Driver(threads);
    }

    @Setup(Level.Invocation)
    public void reset() {
        population = algorithm.create(levels);
        driver.data = data(population);
    }

    /**
     * @return a value derived from the returned ranks, see {@link ConcurrentInsertionDriver}
     */
    @Benchmark
    public int insertAll() {
        return switch (algorithm) {
            case LEVEL -> driver.sortUsingJFBY();
            case COARSE -> driver.sortUsingTsJFBY();
            case FINE, FINE_RANK_HINT -> driver.sortUsingSyncJFBY();
            case CAS -> driver.sortUsingCJFBY();
            case CAS_ONE_BY_ONE -> driver.sortUsingCJFBYAlt();
        };
    }

    /**
     * @return the population of the current invocation (for tests)
     */
    ManagedPopulation<Void> population() {
        return population;
    }

    /**
     * @return the driver's data with {@code population} in the slot of the algorithm, the other slots empty
     */
    private ConcurrentInsertionData data(final ManagedPopulation<Void> population) {
        return new ConcurrentInsertionData(
                algorithm == ConcurrentAlgorithm.LEVEL ? (JfbyPopulation<Void>) population : null,
                algorithm == ConcurrentAlgorithm.COARSE ? (CoarseLockJfbyPopulation<Void>) population : null,
                algorithm == ConcurrentAlgorithm.CAS ? (CasJfbyPopulation<Void>) population : null,
                algorithm == ConcurrentAlgorithm.CAS_ONE_BY_ONE ? (CasJfbyPopulation<Void>) population : null,
                algorithm == ConcurrentAlgorithm.FINE || algorithm == ConcurrentAlgorithm.FINE_RANK_HINT
                        ? population : null,
                addends, addendsByThread);
    }

    /**
     * The driver with this benchmark's data; one per trial, used from the JMH thread only.
     */
    private static final class Driver extends ConcurrentInsertionDriver {
        private final int threads;
        private ConcurrentInsertionData data;

        Driver(final int threads) {
            this.threads = threads;
        }

        @Override
        protected ConcurrentInsertionData getPreparedTestData() {
            return data;
        }

        @Override
        protected int nThreads() {
            return threads;
        }
    }
}
