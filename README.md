# YB18 INDS framework

[![CI](https://github.com/yakupov/YB18-inds-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/yakupov/YB18-inds-framework/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.yakupov/inds-core)](https://central.sonatype.com/artifact/io.github.yakupov/inds-core)

Incremental and asynchronous non-dominated sorting (INDS) for steady-state evolutionary multi-objective optimization:
the algorithms of the thesis *Efficient Incremental and Asynchronous Non-Dominated Sorting for Evolutionary
Multiobjective Algorithms* (I. Yakupov, ITMO University, 2019), their baselines, the benchmark data and the
benchmarks, as one buildable, MIT-licensed Java 17 library.

The algorithm code is the code the thesis and the papers cite, consolidated from the original research repositories
(2015–2020) with new package and class names, and verified to behave and perform the same
([verification](docs/verification.md)), apart from fixed defects ([changes](docs/changes-from-cited-code.md)).

## Algorithms

Labels follow the thesis.

**Contributions** (module `inds-core`):

| Label | Idea | Class |
|---|---|---|
| DC1 | Divide-and-conquer sorting restricted to the points dominated by the inserted one, with the is-anything-changed heuristic (GECCO 2017) | `JfbSorter#performIncrementalNds` |
| DC2 | DC1 with linear-time sweeps, using "ranks change by at most one" (GECCO 2017) | `IncrementalJfbSorter` |
| Level | Level-by-level update that calls the divide-and-conquer step only on affected subsets; incremental crowding distance (GECCO 2017, 2018) | `JfbyPopulation` |
| Coarse | Level behind a global lock: the asynchronous baseline (GECCO 2018) | `CoarseLockJfbyPopulation` |
| Fine | One lock per level, hand over hand (GECCO 2018). Two versions: as in the thesis, and the 2019 revision that retries from a rank hint | `FineLockJfbyPopulation`, `FineLockJfbyPopulationRankHint` |
| CAS | Lock-free levels via compare-and-set with time-stamping (GECCO 2018) | `CasJfbyPopulation` |
| 2D | Levels stored in treaps; O(N) worst-case insertion, sorting by the first two objectives (CEC 2015); crowding distances on demand (GECCO 2015) | `TwoDimTreapPopulation` |

**Baselines:**

| Label | Algorithm | Where |
|---|---|---|
| DC | Offline divide-and-conquer non-dominated sorting (Jensen; Fortin et al.; Buzdalov & Shalyto, PPSN 2014); used inside the incremental algorithms | `inds-core`: `JfbSorter#performNds` |
| ENLU | Efficient Non-domination Level Update (Li et al., 2017): a population with crowding distances behind a global lock | `inds-baselines`: `EnluPopulation` |

Populations support what a steady-state EMOA needs: insert a point, query points with rank and crowding distance,
remove the worst point. How they work: [architecture](docs/architecture.md).

## Modules

| Module | Contents |
|---|---|
| `inds-core` | API (`Individual`, `ManagedPopulation`, `NonDominationLevel`, `PopulationSnapshot`) and the algorithms above; no runtime dependencies besides jsr305 annotations |
| `inds-baselines` | ENLU |
| `inds-testdata` | loader and model of the benchmark data sets (`DataSets`) |
| `inds-benchmark-data` | the data sets of the thesis experiments (~1.9 GB of JSON, resources only): NSGA-II population snapshots and insertion sequences for DTLZ / ZDT problems, synthetic sets ([README](inds-benchmark-data/README.md)) |
| `inds-benchmarks` | JMH benchmarks of the thesis experiments |

## Use from Maven

`inds-core` (API, sorters, populations, 2D) and `inds-baselines` (ENLU) are on Maven Central:

```xml
<dependency>
    <groupId>io.github.yakupov</groupId>
    <artifactId>inds-core</artifactId>
    <version>0.2.1</version>
</dependency>
```

Requires Java 17; the only runtime dependency is the jsr305 annotations. The data sets and benchmarks are not
published: clone the repository for them.

## Quick start

Prerequisites: JDK 17 or newer, Maven 3.9 or newer.

```bash
mvn verify                                                  # build everything, run all tests (a few minutes)
mvn -pl inds-core test -Dtest=JfbyPopulationCorrectnessTest  # one test class
mvn -pl inds-core,inds-baselines verify -Pstress            # long concurrency stress runs (~15 min)
```

### Use a population

```java
ManagedPopulation<MyPayload> population = new JfbyPopulation<>(expectedSize);   // Level
int rank = population.addIndividual(new FitnessAndCdIndividual<>(objectives, payload));
PopulationSnapshot<MyPayload> snapshot = population.getSnapshot();             // levels, members, crowding distances
```

All objectives are minimized; `addIndividual` evicts the worst points above the expected size
([architecture](docs/architecture.md#population-api)). For concurrent insertion use `FineLockJfbyPopulation`,
`CasJfbyPopulation` or `CoarseLockJfbyPopulation`; in the thesis experiments Fine was the fastest for practical thread
counts.

### Load a data set

```java
FrontsAndAddends s = DataSets.snapshots("snapshots/dtlzNd/dtlz2_dim6_gen10000_iter10000_dataset1.json").get(5000);
double[][] population = s.population();   // 10 000 points after 5 000 iterations, front by front
double[] next = s.addends().get(0);        // the point inserted next
```

### Run benchmarks

```bash
mvn -pl inds-benchmarks -am package -DskipTests
java -jar inds-benchmarks/target/benchmarks.jar "\.InsertionBenchmark\." -p algorithm=LEVEL,ENLU \
  -p snapshot=snapshots/dtlzNd/dtlz2_dim6_gen10000_iter10000_dataset1.json#5000
```

Benchmarks, parameters, more examples and how to get comparable numbers:
[inds-benchmarks/README.md](inds-benchmarks/README.md).

## Documentation

- [architecture.md](docs/architecture.md): modules, packages, API, how the populations work, data model, tests
- [verification.md](docs/verification.md): how the library was checked against the cited code (golden master,
  oracle, reference model, stress tests, benchmarks)
- [changes-from-cited-code.md](docs/changes-from-cited-code.md): what differs from the cited code where it can
  change results: fixes, known limitations, structure
- [inds-benchmarks/README.md](inds-benchmarks/README.md): running the benchmarks
- [reproducibility.md](docs/reproducibility.md): how the thesis experiments map to this repository, and how to
  re-run the Chapter 3 figures
- [provenance.md](docs/provenance.md): the origin of every ported file
- [golden-records.md](docs/golden-records.md): format of the golden-master records
- [CONTRIBUTING.md](CONTRIBUTING.md): rules for changing the code; [releasing.md](docs/releasing.md): publishing a
  release
- [Issues](https://github.com/yakupov/YB18-inds-framework/issues): open work (defects to fix, improvements, ideas);
  [releases](https://github.com/yakupov/YB18-inds-framework/releases): what changed in each version

## NSGA-II experiments

The steady-state and asynchronous NSGA-II of the thesis (Chapters 2 and 4) depend on the MOEA Framework (LGPL) and
will live in a separate repository that uses this library. Until then, see
[ssNSGAII2018](https://github.com/yakupov/ssNSGAII2018/tree/r2018.9.1).

## Origins

The code comes from the repositories the thesis and the papers cite, which stay unchanged:
[nds2018](https://github.com/yakupov/nds2018) (Ch. 4), [nds2016](https://github.com/yakupov/nds2016) (Ch. 3),
[nds2018benchmarks](https://github.com/yakupov/nds2018benchmarks) and
[nds2018Cbenchmarks](https://github.com/yakupov/nds2018Cbenchmarks); origin of every file:
[provenance](docs/provenance.md). Also cited, not used here: [ssNSGAII2018](https://github.com/yakupov/ssNSGAII2018)
(Ch. 4 NSGA-II) and M. Buzdalov's
[2015-gecco-nsga-ii-ss](https://github.com/mbuzdalov/papers/tree/master/2015-gecco-nsga-ii-ss) (Ch. 2). His
[non-dominated-sorting](https://github.com/mbuzdalov/non-dominated-sorting) library (MIT) is the independent oracle in
the tests.

## Publications

1. M. Buzdalov, I. Yakupov, A. Stankevich. Fast Implementation of the Steady-State NSGA-II Algorithm for Two
   Dimensions Based on Incremental Non-Dominated Sorting. GECCO 2015, pp. 647–654.
2. I. Yakupov, M. Buzdalov. Incremental Non-Dominated Sorting with O(N) Insertion for the Two-Dimensional Case. IEEE
   CEC 2015, pp. 1853–1860.
3. I. Yakupov, M. Buzdalov. Improved Incremental Non-dominated Sorting for Steady-State Evolutionary Multiobjective
   Optimization. GECCO 2017, pp. 649–656.
4. I. Yakupov, M. Buzdalov. On Asynchronous Non-Dominated Sorting for Steady-State Multiobjective Evolutionary
   Algorithms. GECCO 2018 Companion, pp. 205–206. [arXiv:1804.05208](https://arxiv.org/abs/1804.05208).

To cite the library, cite the thesis: I. Yakupov. *Efficient Incremental and Asynchronous Non-Dominated Sorting for
Evolutionary Multiobjective Algorithms*. PhD thesis, ITMO University, Saint Petersburg, 2019; and the paper of the
algorithm you use. Machine-readable: [CITATION.cff](CITATION.cff).

## License

MIT, see [LICENSE](LICENSE). Dependencies are MIT-compatible; JMH (GPLv2 with the Classpath Exception) is used only by
the benchmark module.
