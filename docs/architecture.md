# Architecture

## Modules

```
inds-core            API, sorters, populations, 2D          (runtime: jsr305 annotations only)
  ▲   ▲
  │   └── inds-baselines        ENLU population
  └────── inds-testdata         data-set model and loader     (Gson)
                ▲
inds-benchmark-data             data sets, resources only (~1.9 GB)
inds-benchmarks                 JMH benchmarks on all of the above
```

Test scope: M. Buzdalov's `non-dominated-sorting-implementations` (the oracle) and inds-core's test-jar, which carries
the shared test infrastructure to the other modules.

## Packages of inds-core (`org.itmo.inds`)

| Package | Contents |
|---|---|
| `api` | `Individual` and `FitnessAndCdIndividual`, `ManagedPopulation`, `NonDominationLevel`, `PopulationSnapshot`, `RankedIndividual` |
| `sorting` | `JfbSorter` (DC full sort, DC1 insertion, `addRankedMembers` for the levels), `IncrementalJfbSorter` (DC2) |
| `population` | `JfbyPopulation` (Level), `CoarseLockJfbyPopulation`, `FineLockJfbyPopulation` / `FineLockJfbyPopulationRankHint`, `CasJfbyPopulation`; their level type `JfbyNonDominationLevel` |
| `twod` | `TwoDimTreapPopulation` (2D) and its treap |
| `util` | dominance and lexicographic comparison, crowding-distance maintenance (`SortedObjectives`), quickselect |

Each package's `package-info.java` maps its classes to the thesis labels and papers. inds-baselines:
`org.itmo.inds.baselines.enlu`; inds-testdata: `org.itmo.inds.testdata`.

## Population API

All objectives are minimized. A population is a set of points: a point equal to a present one (same objective vector)
is not added again.

```java
public interface ManagedPopulation<T> extends Cloneable {
    int addIndividual(Individual<T> individual);   // insert; returns the rank of the point (0 = non-dominated)
    PopulationSnapshot<T> getSnapshot();           // levels in rank order, members with crowding distances; read-only
    int size();
    ManagedPopulation<T> clone();
    // default methods built on getSnapshot(): determineRank, getRandomSolutions, toRankedPopulation
}
```

A population has an *expected size*. Above it, `addIndividual` evicts the member of the last level with the smallest
crowding distance; Fine evicts in batches once the size exceeds 1.2 × the expected size. Unlimited: `Long.MAX_VALUE`
(CAS: `Integer.MAX_VALUE`).

## How the populations work

| Population | Levels | Insertion | Thread safety |
|---|---|---|---|
| Level | list of immutable levels | find the rank, then insert level by level; points a level evicts go to the next (thesis 3.2.5) | none |
| Coarse | as Level | as Level | every method `synchronized` |
| Fine | as Level, one lock per level | as Level, hand over hand: lock *i + 1* before publishing level *i* | locks in ascending order |
| CAS | time-stamped level versions | build a new version, publish it with compare-and-set, retry on conflict | lock-free levels |
| ENLU | its own levels | ENLU level update (Li et al. 2017) | every method `synchronized` |
| 2D | one treap per level, ordered by the first objective | cut the changed segment out of each affected level and move it on (CEC 2015) | none |

Every change of a level builds a new level, so readers never see a half-updated one; `getSnapshot()` of Fine and CAS
is still not linearizable. Levels keep their members sorted per objective (`SortedObjectives`), so crowding distances
are updated incrementally (thesis 4.2.4); 2D computes them on demand. Details: the classes' `package-info`.

## Data model and loader (inds-testdata)

`DataSets` loads a data set of inds-benchmark-data by its path as `FrontsAndAddends`: a population split into
non-domination fronts plus the points to insert.

- **snapshots** (`snapshots/…`, thesis Chapter 3): several snapshots of one run per file, each with the point
  inserted next (`DataSets.snapshots(path)`, by snapshot id: generation, iteration or sample);
- **sequences** (`sequences/…`, thesis Chapter 4): one population and 1 001 points to insert
  (`DataSets.sequence(path)`).

Underneath is the original JSON model (`FrontStorage`, `RunConfiguration`, `DoublesGeneration`, `Front`,
`DoublesAdditionProblem`); its field names are the file format. File list and sources:
[inds-benchmark-data/README.md](../inds-benchmark-data/README.md).

## Benchmarks (inds-benchmarks)

Three JMH benchmarks, each parametrized by algorithm and data set; how they build and reset their inputs is in the
package documentation of `org.itmo.inds.benchmarks`, usage in [inds-benchmarks/README.md](../inds-benchmarks/README.md).

## Tests

| Package (test sources) | Contents |
|---|---|
| `population`, `sorting` | ported legacy unit tests (`LegacySorterShim` gives the nds2016 sorter tests their old API); `ExpectedSizeTest` |
| `golden` | golden-master replay (`GoldenMasterTest`, `EnluGoldenMasterTest`); format: [golden-records.md](golden-records.md) |
| `oracle` | the independent `Oracle`, seeded `OracleInputs`; `ReferencePopulation` and `ModelCrossCheck` (populations against the reference model); `PopulationOracle` (2D, `clone()`) |
| `stress` | `AbstractConcurrentInsertionStressTest` with one subclass per module |
| `twod`, `baselines.enlu` | 2D against its own reference model (`ReferenceTwoDimPopulation`); ENLU against `ReferencePopulation` |
| `testdata`, `benchmarks` | data-set catalogue, benchmark inputs and measured operations against the oracle |

Shared infrastructure lives in inds-core's test sources and reaches the other modules through the test-jar. What each
layer guarantees: [verification.md](verification.md).
