# inds-benchmarks

JMH benchmarks of the algorithms, on the data sets of [inds-benchmark-data](../inds-benchmark-data/README.md).
How they work inside: package documentation of `org.itmo.inds.benchmarks`; which thesis experiment uses which
benchmark and data: [docs/reproducibility.md](../docs/reproducibility.md).

## Build and list

```bash
mvn -pl inds-benchmarks -am package -DskipTests      # inds-benchmarks/target/benchmarks.jar, data included
java -jar inds-benchmarks/target/benchmarks.jar -l   # list the benchmarks
java -jar inds-benchmarks/target/benchmarks.jar -lp  # ... with their parameters and default values
```

## Benchmarks

| Benchmark | Measures | Parameters (`-p name=value,value,…`) |
|---|---|---|
| `InsertionBenchmark` | one insertion into a population snapshot | `algorithm`: `DC`, `DC1`, `DC2`, `LEVEL`, `ENLU` (population); `snapshot`: `path#id` |
| `TwoDimInsertionBenchmark` | one insertion into the 2D population | `snapshot`: `path#id` of a two-objective data set |
| `ConcurrentInsertionBenchmark` | inserting a sequence of 1 001 points into a population of 5 000 with several threads | `algorithm`: `LEVEL` (one thread), `COARSE`, `FINE`, `FINE_RANK_HINT`, `CAS`, `CAS_ONE_BY_ONE`; `sequence`: path; `threads` |

A **snapshot** is named by its file path and snapshot id, `path#id`: the path under `org/itmo/inds/data/` of
`inds-benchmark-data`, the id as listed in its [README](../inds-benchmark-data/README.md) (generational runs: the
generation 0, 10, …, 90; steady-state runs: the iteration 0, 1000, …, 10000; synthetic sets: the sample 0, 1, 2).
JMH runs every combination of the given parameter values; without `-p`, each benchmark runs its defaults (all
algorithms on a few representative data sets: for the insertion benchmarks early populations with many levels and
converged ones with few, large levels; about 2 hours for all three).

Defaults of the settings: average time in µs, 12 warm-up and 4 measurement iterations of 1 s, 3 forks; override with
`-wi`, `-i`, `-f`, `-w`, `-r`. Results: `-rf json -rff results.json`.

## Examples

```bash
# Level and the ENLU population (both maintain crowding distances) on one steady-state snapshot
java -jar inds-benchmarks/target/benchmarks.jar "\.InsertionBenchmark\." -p algorithm=LEVEL,ENLU \
  -p snapshot=snapshots/dtlzNd/dtlz2_dim6_gen10000_iter10000_dataset1.json#5000

# All algorithms on generations 0, 50 and 90 of a generational run (thesis Ch. 3 data), results as JSON
FILE=snapshots/generational/dtlz3d/dtlz1_dim3_gen10000_iter100_dataset1.json
java -jar inds-benchmarks/target/benchmarks.jar "\.InsertionBenchmark\." \
  -p snapshot="$FILE#0,$FILE#50,$FILE#90" -rf json -rff dtlz1-3d.json

# The 2D population on a ZDT1 snapshot
java -jar inds-benchmarks/target/benchmarks.jar "TwoDimInsertionBenchmark" \
  -p snapshot=snapshots/generational/zdt/zdt1_gen10000_iter100_dataset1.json#50

# Fine and CAS with 12 threads on the default sequence
java -jar inds-benchmarks/target/benchmarks.jar "ConcurrentInsertionBenchmark" -p algorithm=FINE,CAS_ONE_BY_ONE -p threads=12

# Quick check (seconds per benchmark, numbers not reliable)
java -jar inds-benchmarks/target/benchmarks.jar "TwoDimInsertionBenchmark" -f 1 -wi 1 -i 1 -w 1s -r 1s
```

## Comparable numbers

Benchmark numbers are machine-specific. To compare two versions of the code, run both on the same machine and JDK,
with the JVM pinned to fixed cores (Linux `taskset -c 0-5 java …`, Windows `start /affinity FFF java …` in `cmd`),
the machine otherwise idle, and the same settings. A difference within a few percent, or with overlapping confidence
intervals, is noise; re-run before drawing conclusions.
