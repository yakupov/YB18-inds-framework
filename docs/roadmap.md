# Roadmap

Open work: the known defects of the cited code and planned or possible improvements. Nothing here is scheduled yet.
Items keep their IDs (F1, F2, …) when they are done, closed or split, so that commits and documents can refer to
them; finished items move to [Done and closed](#done-and-closed).

Changes to algorithm code follow [CONTRIBUTING.md](../CONTRIBUTING.md): a test that shows the defect first, a fix in
a commit of its own, an entry in [changes-from-cited-code.md](changes-from-cited-code.md), golden records updated
where behaviour changes, and a performance comparison. Each item names the checks it has to pass (*gate*): the
golden-master, oracle and stress tests of `mvn verify` ([verification.md](verification.md)), and JMH on the same
machine before and after.

## Known defects of the cited code

Kept as they are, so that the code stays the cited code; the tests record them (`TwoDimTreapPopulationKnownBugsTest`,
golden-master records).

| Where | Defect | Item |
|---|---|---|
| Level, Coarse | `size()` undercounts and eviction is skipped when an insertion pushes a whole level down | F19 |
| Level, Coarse, Fine | `clone()` throws (Level, Coarse) or returns a copy that shares state with the original (Fine) | F20 |
| 2D | every `toString` that reaches a point throws `IllegalFormatConversionException` | F21 |
| 2D | `detRankOfExPoint` / `getRandWithRank` fail while the population has exactly one level | F15 |
| 2D | `TwoDimPoint` has a static, unsynchronized comparison counter shared by all populations and threads | F14 |
| CAS | retry loops swallow index errors of the sorting and crowding-distance code (endless retry); a removed last level is waited for by spinning | F11 |
| CAS | the logical clock (`AtomicLong`) has no overflow handling (`todo` in the code) | none: 2⁶³ ticks are out of reach |
| Fine, CAS | `getSnapshot()` is not linearizable: points moving between levels can be missing, and `size()` can disagree with the snapshot during insertions | none: inherent in the lock / CAS design; documented in [architecture.md](architecture.md) |
| `AbstractConcurrentJfbyPopulation` | unused (nothing extends it) | F10 |
| `ManagedPopulationCorrectnessTest.testRand` (ported test) | seeded with `System.nanoTime()`, so not reproducible; `@Ignore`d in the cited code and here | none |

## Correctness and robustness

- **F19 — Level / Coarse size bookkeeping.** `JfbyPopulation.addIndividual` (Coarse inherits it) returns early in the
  branch where the inserted point pushes a whole level down: before `++size` and before the eviction check. So
  `size()` undercounts and the population can grow beyond its expected size (example: a dominance chain inserted
  worst first with expected size 25 keeps all 50 points; CAS and ENLU evict to 25). Fix: count the point and run the
  eviction check in that branch too. Gate: the affected golden records change (re-recorded and explained in the same
  commit), oracle and stress tests with an exact size check for Level and Coarse.
- **F20 — `clone()` of Level, Coarse and Fine.** Level / Coarse pass the original level list to the copy and then add
  copies to the unmodifiable snapshot list: `UnsupportedOperationException` for any non-empty population. Fine's copy
  shares the original `CopyOnWriteArrayList` and puts its level copies into a list that is thrown away: inserting into
  the copy changes the original. Fix: return independent copies (or drop `Cloneable` from the API). Gate: the golden
  clone scenarios change (re-recorded), new tests that the copy and the original evolve independently.
- **F21 — 2D `toString`.** `TwoDimPoint.toString` uses `String.format("ind %d %d", x1, x2)` with doubles; the
  `toString` of `TwoDimTreap` and `TwoDimTreapPopulation` and every error message that prints a point throw with it.
  Fix: `%s` (or `%f`). Gate: `TwoDimTreapPopulationKnownBugsTest` updated; golden records do not call `toString`.
- **F15 — 2D `detRankOfExPoint` with one level.** The binary search is skipped when there is only one level; the
  branch after the loop then sets `result = 0` but does not return, and the final `if (result > 0)` rejects rank 0,
  so every call ends in "Can't determine rank" (thrown as `IllegalFormatConversionException` because of F21).
  Fix: accept `result >= 0` (or return from that branch). Gate: `TwoDimTreapPopulationKnownBugsTest` updated, oracle
  cross-check extended to one-level populations; the golden records do not call the method.
- **F11 — CAS retry loops.** (a) In `CasJfbyPopulation.insertOneByOne`, `insertViaFullSortings` and `intRemoveWorst`
  the `try { … } catch (ArrayIndexOutOfBoundsException ignored)` that handles a level removed by another thread
  (`CopyOnWriteArrayList.get`) wraps the whole step, including `performNds`, `SortedObjectives.update` and the
  crowding-distance code: an index bug there is retried forever without a trace, while `IndexOutOfBoundsException`
  from `ArrayList.get` escapes. Narrow each `try` to the level-list accesses. (b) `levelRef == null → continue`
  spins: `null` exists only while `intRemoveWorst` holds `addRemoveLevelLock` between its compare-and-set and the
  removal from the list. On `null`, wait for the remover (`addRemoveLevelLock.lock(); addRemoveLevelLock.unlock();`)
  and retry: no cost on the normal path, no spinning while the remover is descheduled, no new lock order.
  Gate: stress tests including F12, JMH.
- **F12 — Stress-test eviction.** The stress tests use an unlimited expected size, so eviction (Fine: batch removal
  above 1.2 × the expected size under the level lock plus the add- and remove-level locks; CAS: one point per insertion
  under `addRemoveLevelLock`) never runs concurrently with insertions. Add scenarios with small expected sizes:
  size bound after quiescence, levels consistent and non-dominated, every remaining point once, removed points have
  the worst crowding distance of the last level where this can be checked.
- **F10 — `AbstractConcurrentJfbyPopulation`** is unused (nothing extends it, also not in the cited code): keep or
  delete.

## API

- **F14 — 2D API.** (a) Replace `TwoDimPoint` (own point type with a static comparison counter and the `toString` of
  F21) by the library's point / `Individual` types. (b) Give the 2D population the insertion and rank-query interface
  of the other populations, or an adapter to `ManagedPopulation`. Both change signatures of ported code.
  Gate: golden 2D records (or re-recorded equivalents), oracle cross-check, JMH.
- **F2 — Full 2D steady-state population** (removal, i-th point query, crowding distance) as described in thesis
  Chapter 2. The original implementation is in M. Buzdalov's repository, which has no license: porting it needs his
  permission; otherwise decide not to.
- **F18 — A common sorter interface** for DC / DC1 / DC2 (marked `todo` in `JfbSorter`).

## Build and tooling

- **F16 — Static bug finding in `mvn verify`.** SpotBugs (`spotbugs-maven-plugin`), Error Prone with NullAway (the
  jsr305 annotations are already on the classpath) or PMD, failing on new findings. Ported classes cannot change,
  so their existing findings go into an exclusion list generated from [provenance.md](provenance.md) (reviewed once;
  real defects become items here). It should catch at least what code inspections found in new code so far: possible
  `null` dereferences, ignored return values (SpotBugs `RV_RETURN_VALUE_IGNORED`, Error Prone `CheckReturnValue` /
  `FutureReturnValueIgnored`), broken Javadoc references (`-Xdoclint`), raw types and unchecked conversions
  (`-Xlint`), unused members, missing `@Nonnull` on overrides (NullAway). Gate: no finding in new code, build time
  acceptable.
- **F17 — Linting in `mvn verify`.** Checkstyle with rules matching `.editorconfig` and CONTRIBUTING.md (braces, line
  length 120, naming, unused imports, Javadoc on types) and `javac -Xlint:all` (`-Werror` for new code if feasible),
  ported classes excluded as in F16. Headless IntelliJ inspections (`inspect.sh` / `inspect.bat`) were tried as a
  local check and rejected: the analysis can start before indexing has finished (stale or empty reports that look
  like success), runs fail intermittently on project setup, a crashed run leaves a lock that survives reboots, and the
  setup depends on the local IDE installation. If IntelliJ's inspections are wanted reproducibly, JetBrains Qodana
  runs them in a container; compare its cost and noise with Checkstyle / PMD. Gate: clean on new code.
- **F8 — JSON library.** The data model uses Gson 2.8 (with a type adapter for JDK 17); move to a current Gson or
  another library (e.g. Jackson) while keeping the existing data files readable.

## Data

- **F6 — Data-set storage.** `inds-benchmark-data` adds about 1.9 GB to every checkout (about 0.5 GB compressed in a clone). Options: release assets, Git LFS,
  a separate data repository, gzipped files, or regenerating the sets from seeds.
- **F1 — Synthetic 2D data sets** of the thesis Chapter 2 experiments (square, parallel, diag1, diag2, parper;
  thesis Section 2.3.1). The generator code was not found; locate it or recreate it from the thesis, then add the
  sets and 2D benchmarks on them.

## Experiments

- **F3 — NSGA-II experiments repository.** The steady-state and asynchronous NSGA-II of thesis Chapters 2 and 4
  (`ssNSGAII2018` `r2018.9.1`, the Chapter 4 configurations) and the data-set generators of nds2016 depend on the
  LGPL MOEA Framework and will live in a separate repository that uses this library.
- **F22 — Sharded levels for Fine (research idea).** Based on A. Polozenko's BSc thesis (ITMO, 2021;
  [AlexandraPolozenko/NSGAII2021](https://github.com/AlexandraPolozenko/NSGAII2021)), which explored lock order,
  removal and level sharding to make Fine scale with more threads in steady-state NSGA-II. Motivation: the time of
  one insertion grows with the number of threads, and the threads meet at the last level and, near convergence, at
  one large front, where every insertion rewrites the same level under one lock. Idea: split a level into shards
  with locks of their own. Open questions: a lock protocol that keeps the cascade correct (Fine's hand-over-hand
  order across all shards of a level), the dominance check under the lock, and global or per-shard crowding distance.
  Gate: stress tests including F12, JMH against Fine with a near-front workload.

## Done and closed

- **F4 — Continuous integration** (done). GitHub Actions ([ci.yml](../.github/workflows/ci.yml)) runs `mvn verify`
  with JDK 17 on every push to `main` and every pull request; the long stress runs and JMH stay manual.
- **F7 — Publishing to Maven Central** (done, `0.1.0`). Group id `io.github.yakupov` (package names stay
  `org.itmo.inds`); `inds-parent`, `inds-core` and `inds-baselines` with sources, Javadoc and signatures; release
  steps in [CONTRIBUTING.md](../CONTRIBUTING.md#releasing).
- **F5 — Decide on the defects kept from the cited code:** split into one item per defect (F19, F20, F21; F15 and
  F11 existed already).
- **F9 — Benchmark populations from recomputed fronts** (done). Two data sets store fronts that differ from the true
  non-domination levels (ZDT1 at 10 000 iterations: 440 points; two-planes with 5 objectives: 44 points), so
  benchmarks build their populations from recomputed fronts. The benchmarks of this library must do so too.
- **F13 — Fallback for the Fine fix** (closed: not needed). The fix acquires the lock of level *i + 1* before
  publishing level *i*, so lock *i* is held a little longer. The fallback would have kept the original lock order and
  filtered dominated points in the cascade instead. The benchmarks show no cost of the new lock order (Fine within
  ±4 % of the cited code at 3, 6 and 12 threads), so it stays.
