package org.itmo.inds.benchmarks;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.testdata.SyntheticProblem;
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

import java.util.concurrent.TimeUnit;

/**
 * The time to insert all points of a synthetic problem ({@link SyntheticProblem}, thesis Section 2.3.1) one by one
 * into an empty population. The points of the random problems are drawn from {@link #SEEDS} seeds, one per
 * invocation in turn, so that the average is over several point sets (the thesis averages 100 runs).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class TwoDimSequenceBenchmark {
    /**
     * Number of different point sets of a random problem that the invocations cycle through.
     */
    static final int SEEDS = 10;

    @Param
    public TwoDimSequenceAlgorithm algorithm;

    @Param
    public SyntheticProblem problem;

    @Param({"250", "500", "1000", "2000", "4000"})
    public int size;

    private FitnessAndCdIndividual<Void>[][] sequences;
    private int invocation;
    private ManagedPopulation<Void> population;

    @Setup(Level.Trial)
    @SuppressWarnings("unchecked")
    public void prepare() {
        final int sequenceCount = problem.isRandom() ? SEEDS : 1;
        sequences = new FitnessAndCdIndividual[sequenceCount][];
        for (int seed = 0; seed < sequenceCount; ++seed) {
            final double[][] points = problem.points(size, seed);
            sequences[seed] = new FitnessAndCdIndividual[points.length];
            for (int i = 0; i < points.length; ++i) {
                sequences[seed][i] = new FitnessAndCdIndividual<>(points[i], null);
            }
        }
    }

    @Setup(Level.Invocation)
    public void reset() {
        population = algorithm.createPopulation();
    }

    /**
     * @return the size of the population after inserting all points
     */
    @Benchmark
    public int insertAll() {
        final FitnessAndCdIndividual<Void>[] sequence = sequences[invocation++ % sequences.length];
        for (final FitnessAndCdIndividual<Void> individual : sequence) {
            population.addIndividual(individual);
        }
        return population.size();
    }
}
