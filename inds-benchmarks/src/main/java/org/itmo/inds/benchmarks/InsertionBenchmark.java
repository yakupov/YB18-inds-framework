package org.itmo.inds.benchmarks;

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
 * Thesis Chapter 3: the time to insert one point (the snapshot's next point) into a population of a data-set
 * snapshot, per algorithm. Default snapshots: for DTLZ1 (3 objectives), DTLZ2 (6 objectives) and ZDT1, the populations
 * of a steady-state NSGA-II run after 0, 5 000 and 10 000 iterations (early populations with many levels) and of a
 * generational run at generation 90 (converged populations with few, large levels); three synthetic populations
 * with 5 objectives. Any snapshot of {@code inds-benchmark-data} can be given with {@code -p snapshot=path#id}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 12, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(3)
public class InsertionBenchmark {
    @Param
    public InsertionAlgorithm algorithm;

    @Param({
            "snapshots/dtlz3d/dtlz1_dim3_gen10000_iter10000_dataset1.json#0",
            "snapshots/dtlz3d/dtlz1_dim3_gen10000_iter10000_dataset1.json#5000",
            "snapshots/dtlz3d/dtlz1_dim3_gen10000_iter10000_dataset1.json#10000",
            "snapshots/dtlzNd/dtlz2_dim6_gen10000_iter10000_dataset1.json#0",
            "snapshots/dtlzNd/dtlz2_dim6_gen10000_iter10000_dataset1.json#5000",
            "snapshots/dtlzNd/dtlz2_dim6_gen10000_iter10000_dataset1.json#10000",
            "snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#0",
            "snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#5000",
            "snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#10000",
            "snapshots/generational/dtlz3d/dtlz1_dim3_gen10000_iter100_dataset1.json#90",
            "snapshots/generational/dtlzNd/dtlz2_dim6_gen10000_iter100_dataset1.json#90",
            "snapshots/generational/zdt/zdt1_gen10000_iter100_dataset1.json#90",
            "snapshots/constant/uniform_dim5_gen10000.json#0",
            "snapshots/constant/twoLayers_dim5_gen10000.json#0",
            "snapshots/constant/twoLines_dim5_gen10000.json#0",
    })
    public String snapshot;

    private PreparedInsertion insertion;

    @Setup(Level.Trial)
    public void prepare() {
        insertion = algorithm.prepare(BenchmarkInput.snapshot(snapshot));
    }

    @Setup(Level.Invocation)
    public void reset() {
        insertion.reset();
    }

    @Benchmark
    public int insert() {
        return insertion.run();
    }
}
