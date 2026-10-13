# Reproducing the thesis experiments

How the experiments of the thesis map to this repository, and how to re-run the ones it contains. Benchmark numbers
are machine-specific: compare shapes and orderings with the thesis, and absolute numbers only between runs on the same
machine and JDK.

## What is here

| Thesis | Measures | In this repository |
|---|---|---|
| Ch. 2, synthetic 2D tests (CEC 2015) | insertion of N points from synthetic 2D sets | **yes**, recreated from the thesis text and figures: `SyntheticProblem`, `TwoDimSequenceBenchmark` ([below](#thesis-chapter-2-synthetic-tests-figures-1519)) |
| Ch. 2, NSGA-II experiments | steady-state / generational NSGA-II with the 2D population | no: NSGA-II experiments live in a separate repository ([#14](https://github.com/yakupov/YB18-inds-framework/issues/14)) |
| **Ch. 3, Figures 21–24** | single-point insertion into NSGA-II populations of 10 000 points, generations 0, 10, …, 90 | **yes**: `InsertionBenchmark` / `TwoDimInsertionBenchmark` on `snapshots/generational/` |
| Ch. 4, Figures 26 and later | wall-clock time of asynchronous NSGA-II runs | no: NSGA-II experiments ([#14](https://github.com/yakupov/YB18-inds-framework/issues/14)). `ConcurrentInsertionBenchmark` measures concurrent insertion on its own, with the 2018 benchmark data (`sequences/`) |

## Thesis Chapter 2, synthetic tests (Figures 15–19)

**Data:** none stored. `SyntheticProblem` generates the five problems (`SQUARE`, `PARALLEL`, `DIAG1`, `DIAG2`,
`PARPER`) from the description in Section 2.3.1; the original generator code was not found. The random problems take a
seed; the order of `PARPER` is read off the figure (N = 24). `TwoDimSequenceBenchmark` inserts all N points one by one
into an empty population and measures the total time, for the 2D population and ENLU, N = 250 … 4 000. The benchmark
cycles through 10 seeds of the random problems instead of the thesis's 100 runs. The thesis also compared the number of
comparisons and the plain fast non-dominated sorting; neither is reproduced here.

```bash
java -jar inds-benchmarks/target/benchmarks.jar "TwoDimSequenceBenchmark" -rf json -rff chapter2.json  # about 20 minutes
```

Total time in ms (average over the runs, 99.9 % confidence intervals within 7 % of the value; JDK 17.0.10, AMD Ryzen 9
7900X3D, JVM pinned to logical processors 0–11, machine idle, 2 forks of 5 warm-up and 5 measurement iterations):

| Problem | Algorithm | N = 250 | 500 | 1 000 | 2 000 | 4 000 |
|---|---|---|---|---|---|---|
| `SQUARE` | 2D | 0.12 | 0.29 | 0.78 | 2.1 | 5.3 |
| | ENLU | 0.81 | 2.4 | 7.8 | 23 | 70 |
| `PARALLEL` | 2D | 0.07 | 0.17 | 0.38 | 0.87 | 2.0 |
| | ENLU | 0.68 | 2.6 | 9.7 | 38 | 163 |
| `DIAG1` | 2D | 0.02 | 0.04 | 0.11 | 0.26 | 0.76 |
| | ENLU | 0.03 | 0.06 | 0.12 | 0.28 | 0.69 |
| `DIAG2` | 2D | 0.45 | 1.8 | 7.2 | 30 | 132 |
| | ENLU | 4.7 | 19 | 77 | 300 | 1 186 |
| `PARPER` | 2D | 0.11 | 0.32 | 0.97 | 3.1 | 11 |
| | ENLU | 2.3 | 9.2 | 38 | 152 | 627 |

The shapes of the thesis are reproduced: 2D beats ENLU on `SQUARE` and `PARALLEL`; the two are about equal on `DIAG1`
(the best case of ENLU); 2D is quadratic in total on `DIAG2` (linear per insertion, the worst case for it) and ENLU is
about nine times slower there; on `PARPER` ENLU is far behind (57 times at N = 4 000). One difference: the thesis finds
ENLU cubic in total on `PARPER` (more than 3·10⁹ comparisons at N = 4 000); here it is quadratic. The ENLU measured
here is the population of the Chapter 4 code, not the levels-only sorter of the thesis ([Limitations](#limitations)).

## Thesis Chapter 3 (Figures 21–24)

**Data:** `snapshots/generational/` of `inds-benchmark-data` (byte-identical to the files the thesis used). Each file
holds one generational NSGA-II run of DTLZ / ZDT with 10 000 points; snapshot `#g` is the population at the
beginning of generation `g` (0, 10, …, 90) plus one point to insert. Several files (`_dataset1`, `_dataset2`, …) per
problem are independent runs; each configuration in the figures is one of them.

| Figure | Problems | Files |
|---|---|---|
| 21 | ZDT1–4, 6 (2 objectives) | `snapshots/generational/zdt/zdt<p>_gen10000_iter100_dataset<d>.json` |
| 22 | DTLZ1–4, 7 (3 objectives) | `snapshots/generational/dtlz3d/dtlz<p>_dim3_gen10000_iter100_dataset<d>.json` |
| 23 | DTLZ1 with 3, 4, 6, 8, 10 objectives | 3: `dtlz3d/dtlz1_dim3_…`; 4–10: `snapshots/generational/dtlzNd/dtlz1_dim<k>_gen10000_iter100_dataset<d>.json` |
| 24 | DTLZ2 with 3, 4, 6, 8, 10 objectives | as Figure 23 with `dtlz2` |

**Algorithms** (`-p algorithm=`): `DC`, `DC1`, `DC2`, `LEVEL`, `ENLU` (in place of the figures' ENLU sorter, see
[Limitations](#limitations)), and on ZDT the 2D population (`TwoDimInsertionBenchmark`).

**Settings** of the thesis: 30 warm-up and 6 measurement iterations, 2 forks (`-wi 30 -i 6 -f 2`); the benchmarks'
own defaults are 12, 4 and 3.

### Limitations

Exact reproduction of the figures is not a goal of this library; the cited repositories remain available for it.
Re-running the figures here differs from the original measurements:

| Difference | The figures | This library | Effect |
|---|---|---|---|
| Code | 2016 implementations of DC, DC1, DC2, Level (nds2016) and a levels-only ENLU sorter | the Chapter 4 code (nds2018); Level and ENLU as populations that also keep crowding distances | Level against ENLU is like for like here, not as in the figures ([structure](changes-from-cited-code.md#structure)); 2D is the measured code |
| Measurement | the harness rebuilt all structures before every insertion, so Level and the ENLU sorter ran interpreted (40–70 runs per fork, observed with `-XX:+PrintCompilation`), 2D compiled | only the measured structure is rebuilt; every algorithm runs compiled | the microsecond-scale orderings (2D fastest for two objectives, Level against ENLU) are not expected to reproduce; DC, DC1, DC2 (milliseconds) much less affected |

### Example

Running the benchmarks (build, parameters, settings, pinning): [inds-benchmarks/README.md](../inds-benchmarks/README.md).
A figure panel is one command; e.g. DTLZ7 with 3 objectives (Figure 22), data set 1, all generations, the
algorithms of the figure and the thesis settings:

```bash
FILE=snapshots/generational/dtlz3d/dtlz7_dim3_gen10000_iter100_dataset1.json
SNAPSHOTS=$(for g in 0 10 20 30 40 50 60 70 80 90; do printf '%s#%s,' "$FILE" "$g"; done)
java -jar inds-benchmarks/target/benchmarks.jar "\.InsertionBenchmark\." -wi 30 -i 6 -f 2 \
  -p algorithm=DC,DC1,DC2,LEVEL,ENLU -p snapshot="${SNAPSHOTS%,}" -rf json -rff dtlz7-3d.json
```

In the file name, `gen10000` is the population size and `iter100` the number of generations the run simulated; the
snapshot id after `#` is the generation (0, 10, …, 90). For ZDT (Figure 21) add `TwoDimInsertionBenchmark` with the
same snapshots. A whole figure is the same command with the snapshots of all its files; it takes hours (about 1–2
minutes per algorithm and snapshot with the thesis settings).

### What to compare

The figures show, per generation, the insertion time of each algorithm (log scale; error bars: minimum and maximum).
Conclusions of the thesis that a re-run with this library should reproduce in shape:

- all incremental algorithms are much faster than DC (sorting from scratch); DC gets faster in later generations,
  most on ZDT, as the number of levels drops;
- DC2 is at most slightly faster than DC1.

The microsecond-scale conclusions (Level faster than the ENLU sorter in 119 of 180 configurations, slower in 31; 2D
much faster for two objectives) depend on the original code and measurement ([Limitations](#limitations)).
