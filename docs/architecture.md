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
the shared test infrastructure (golden master, oracle, stress harness) to the other modules.

## Packages of inds-core (`org.itmo.inds`)

| Package | Contents |
|---|---|
| `api` | `Individual` (objectives, crowding distance, payload) and `FitnessAndCdIndividual`; `ManagedPopulation`; `NonDominationLevel`; `PopulationSnapshot`; `RankedIndividual` |
| `sorting` | divide-and-conquer sorters: `JfbSorter` (DC full sort, DC1 incremental insertion, `addRankedMembers` used by the levels), `IncrementalJfbSorter` (DC2) |
| `population` | populations for any number of objectives: `JfbyPopulation` (Level), `CoarseLockJfbyPopulation`, `FineLockJfbyPopulation` / `FineLockJfbyPopulationRankHint`, `CasJfbyPopulation`; their level type `JfbyNonDominationLevel` |
| `twod` | the two-objective population: `TwoDimTreapPopulation`, `TwoDimTreap`, `TwoDimBucket` (points with equal first two objectives) |
| `util` | dominance and lexicographic comparison (`Utils`, comparators), crowding-distance maintenance (`SortedObjectives`), `RankedPopulation`, `RankSorting` |
| `util.median` | selection used by the sorter (quickselect, median of medians) |

Each package has a `package-info.java` that maps its classes to the thesis labels (DC, DC1, DC2, Level, Coarse,
Fine, CAS, 2D) and papers. inds-baselines has `org.itmo.inds.baselines.enlu` (`EnluPopulation`,
`EnluNonDominationLevel`), inds-testdata has `org.itmo.inds.testdata`.

## Population API

All objectives are minimized. A population is a set of points: inserting a point equal to a present one (same
objective vector) does not add it again.

```java
public interface ManagedPopulation<T> extends Cloneable {
    int addIndividual(Individual<T> individual);   // insert; returns the rank of the point (0 = non-dominated)
    PopulationSnapshot<T> getSnapshot();           // levels in rank order, members with crowding distances; read-only
    int size();
    ManagedPopulation<T> clone();
    // default methods built on getSnapshot(): determineRank, getRandomSolutions, toRankedPopulation
}
```

A population is created with an *expected size*. `addIndividual` keeps it there: once the size exceeds it, the
member of the last level with the worst crowding distance is evicted (Fine evicts in batches once the size exceeds
1.2 × the expected size). An unlimited population: `Long.MAX_VALUE` (CAS: `Integer.MAX_VALUE`).

A `NonDominationLevel` holds the members of one rank in lexicographic order with their crowding distances; its
`addMembers` returns a new level plus the members that the addends now dominate (*evicted*, to be inserted into the
next level).

## How the populations work

| Population | Levels | Insertion | Thread safety |
|---|---|---|---|
| Level (`JfbyPopulation`) | `List` of immutable `JfbyNonDominationLevel`s | binary search for the rank, then level by level: `addMembers` into level *r*, the evicted members into *r + 1*, … (thesis 3.2.5) | none |
| Coarse (`CoarseLockJfbyPopulation`) | as Level (subclass) | as Level | every method `synchronized` |
| Fine (`FineLockJfbyPopulation*`) | `CopyOnWriteArrayList` of immutable levels, one `ReentrantLock` per level, extra locks for adding / removing levels | as Level, holding the lock of level *i* and acquiring *i + 1* before publishing level *i* (hand over hand) | per-level locks, acquired in ascending order; `getSnapshot()` reads without locks (not linearizable) |
| CAS (`CasJfbyPopulation`) | `CopyOnWriteArrayList` of `AtomicReference`s to time-stamped level versions | builds the new level version and publishes it with compare-and-set; retries on conflict. One-by-one: a time-stamp fast path; otherwise several addends at once by full sorting | lock-free levels; a lock only for adding / removing levels; `getSnapshot()` not linearizable |
| ENLU (`EnluPopulation`) | own levels (`EnluNonDominationLevel`) | ENLU level update (Li et al. 2017) | every method `synchronized` |

The levels (`JfbyNonDominationLevel`) are immutable: every change builds a new level, so readers never see a
half-updated level. They keep the members' objectives sorted per objective (`SortedObjectives`) so that crowding
distances are updated incrementally (thesis 4.2.4). Duplicates are detected with a map of present individuals
(`HashMap`, concurrent ones: `ConcurrentHashMap`).

The sorters are stateless and shared. DC1 / DC2 (`performIncrementalNds`) insert one point into a lexicographically
sorted, ranked array and return a new one; `performNds` sorts its argument in place. The levels use
`addRankedMembers`, which assumes that no addend is dominated by a member of the level.

The 2D population (`TwoDimTreapPopulation`) works differently: each level is a persistent treap ordered by the
first objective; an insertion cuts the changed contiguous segment out of each affected level with two splits and moves
it on. It sorts by the first two objectives of the points. A treap node holds a `TwoDimBucket`: its coordinates and
the points that share them; a hash map from coordinates to buckets finds duplicates, and a list of all points gives
random points in O(1). Crowding distances (first two objectives) are not stored: `getRandomSolutions` computes them
from a point's neighbours in its level and the level's extremes, O(log<sup>2</sup> N) per point (as the steady-state
NSGA-II of GECCO 2015); `getSnapshot()` builds all levels with their distances in O(N) and keeps them until the next
change. Above the expected size, an insertion evicts the worst point of the last level (rule as in Level, O(size of the
last level)); points of other levels are never removed. Not thread-safe.

## Data model and loader (inds-testdata)

`DataSets` loads a data set of inds-benchmark-data by its path as `FrontsAndAddends`: a population split into
non-domination fronts plus the points to insert. Two kinds of files:

- **snapshots** (`snapshots/…`, thesis Chapter 3): each file holds several snapshots of one run, each with the one
  point inserted next (`DataSets.snapshots(path)` → map by snapshot id). Generational NSGA-II runs (id = generation;
  the data of the thesis figures, `snapshots/generational/`), steady-state runs (id = iteration) and synthetic sets
  (id = sample; [inds-benchmark-data/README.md](../inds-benchmark-data/README.md));
- **sequences** (`sequences/…`, thesis Chapter 4): one population and a sequence of 1 001 points to insert
  (`DataSets.sequence(path)`).

Underneath is the original JSON model (`FrontStorage`, `RunConfiguration`, `DoublesGeneration`, `Front`,
`DoublesAdditionProblem`): its field names are the file format. `LocalDateTimeAdapter` lets Gson read the files'
time stamps on JDK 17. File list and sources: `inds-benchmark-data/README.md`, `MANIFEST.tsv`.

## Benchmarks (inds-benchmarks)

`org.itmo.inds.benchmarks`: three JMH benchmarks, each parametrized by algorithm (an enum) and data set, so one
class covers what the original benchmarks spread over one class per data set and thread count. `BenchmarkInput`
loads a snapshot or sequence through `DataSets` and regroups the population into its true non-domination fronts (some
data sets store fronts that are not exactly the levels). Every algorithm gets a `PreparedInsertion`: built once per
trial, reset before every measured invocation (e.g. a fresh population from the immutable levels), then measured.
The concurrent benchmark runs the original driver of the cited benchmarks (`ConcurrentInsertionDriver`, thread pool
and round-robin split of the points) with new class names. Parameters and usage: [inds-benchmarks/README.md](../inds-benchmarks/README.md).

## Tests

| Package (test sources) | Contents |
|---|---|
| `population`, `sorting` (inds-core), `population` (inds-baselines) | ported legacy unit tests; `LegacySorterShim` gives the nds2016 sorter tests their old `double[]` API |
| `golden` | golden-master replay: `GoldenMasterTest` (inds-core), `EnluGoldenMasterTest` (inds-baselines); infrastructure and vocabulary in the package's `package-info`; records: `src/test/resources/org/itmo/inds/golden/`, format in [golden-records.md](golden-records.md) |
| `oracle` | `Oracle` (the independent sorter), `OracleInputs` (seeded inputs), `PopulationInvariants` (the expected state of a population), `PopulationOracle` (insert and compare), `OracleCrossCheckTest` |
| `stress` | `AbstractConcurrentInsertionStressTest` (harness) with one subclass per module |
| `twod`, `baselines.enlu` | the 2D population against a simple reference model (`ReferenceTwoDimPopulation`: oracle ranks, crowding distances and eviction by definition) and the ENLU oracle cross-checks; `TwoDimTreapPopulationTest` |
| `testdata` | data-set catalogue checks (`DataSetsTest`), golden inputs vs. data files, the Gson adapter |
| `benchmarks` (inds-benchmarks) | benchmark inputs and every measured operation against the oracle |

Shared infrastructure lives in inds-core's test sources; inds-baselines and inds-testdata use it through the test-jar
(same package names give access to package-private helpers). What each layer protects: [verification.md](verification.md).
