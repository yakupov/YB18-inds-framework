package org.itmo.inds.benchmarks;

import org.itmo.inds.twod.TwoDimPoint;
import org.itmo.inds.twod.TwoDimTreapPopulation;
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
 * The time to insert one point (the snapshot's next point) into the 2D population (two objectives only), built
 * before every insertion by inserting the snapshot's points front by front. Default snapshots: ZDT1 after 0, 5 000
 * and 10 000 iterations of a steady-state run and at generation 90 of a generational run (one level); any
 * two-objective snapshot can be given with {@code -p snapshot=path#id}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 12, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(3)
public class TwoDimInsertionBenchmark {
    @Param({
            "snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#0",
            "snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#5000",
            "snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#10000",
            "snapshots/generational/zdt/zdt1_gen10000_iter100_dataset1.json#90",
    })
    public String snapshot;

    private BenchmarkInput input;
    private double[] addend;
    private TwoDimTreapPopulation population;

    @Setup(Level.Trial)
    public void prepare() {
        input = BenchmarkInput.snapshot(snapshot);
        if (input.k() != 2) {
            throw new IllegalArgumentException(snapshot + " has " + input.k() + " objectives; 2D needs 2");
        }
        addend = input.addends().get(0).clone();
    }

    @Setup(Level.Invocation)
    public void reset() {
        population = new TwoDimTreapPopulation();
        for (final double[] point : input.population()) {
            population.addPoint(new TwoDimPoint(point));
        }
    }

    /**
     * @return the size of the population after the insertion
     */
    @Benchmark
    public int insert() {
        population.addPoint(new TwoDimPoint(addend));
        return population.size();
    }
}
