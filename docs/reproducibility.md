# Reproducing the thesis experiments

How the experiments of the thesis map to this repository, and how to re-run the ones it contains. Benchmark numbers
are machine-specific: compare shapes and orderings with the thesis, and absolute numbers only between runs on the same
machine and JDK.

## What is here

| Thesis | Measures | In this repository |
|---|---|---|
| Ch. 2, synthetic 2D tests (CEC 2015) | insertion of N points from synthetic 2D sets | the 2D population; the synthetic generators are not (roadmap F1) |
| Ch. 2, NSGA-II experiments | steady-state / generational NSGA-II with the 2D population | no: NSGA-II experiments live in a separate repository (roadmap F3) |
| **Ch. 3, Figures 21–24** | single-point insertion into NSGA-II populations of 10 000 points, generations 0, 10, …, 90 | **yes**: `InsertionBenchmark` / `TwoDimInsertionBenchmark` on `snapshots/generational/` |
| Ch. 4, Figures 26 and later | wall-clock time of asynchronous NSGA-II runs | no: NSGA-II experiments (roadmap F3). `ConcurrentInsertionBenchmark` measures concurrent insertion on its own, with the 2018 benchmark data (`sequences/`) |

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
Re-running the figures here differs from the original measurements in two ways:

- **Different code for DC, DC1, DC2, Level and ENLU.** The figures measured the 2016 implementations (nds2016,
  `IncrementalPPSN`) and a levels-only ENLU sorter; this library has the later re-implementations of DC, DC1, DC2
  and Level (nds2018), the code of thesis Chapter 4, and ENLU as a population. Its Level is a full population that
  also maintains crowding distances and de-duplicates points (the 2016 Level only sorted); the ENLU population
  (`ENLU`) does the same, so Level against ENLU is a like-for-like comparison here, but not the one of the figures
  ([changes-from-cited-code.md](changes-from-cited-code.md#structure)). The 2D population is the measured code
  itself.
- **Compiled vs. interpreted code.** The original harness measured the ENLU sorter and Level as interpreted code and
  the 2D treap as compiled code ([changes-from-cited-code.md](changes-from-cited-code.md#benchmarks-measure-compiled-code));
  this library measures every algorithm compiled. The microsecond-scale orderings of the figures (2D fastest for two
  objectives, Level against ENLU) are therefore not expected to reproduce; the millisecond-scale DC, DC1 and DC2 are
  much less affected.

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

The thesis's further conclusions on the microsecond scale (across the 180 configurations of Figures 21–24 Level was
significantly faster than the ENLU sorter in 119, slower in 31, indistinguishable in 30; with two objectives 2D was
much faster than all other algorithms) depend on the original code and measurement conditions (see
[Limitations](#limitations)).
