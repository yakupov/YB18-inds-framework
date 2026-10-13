# Changes from the cited code

The algorithm classes of this library are the code the thesis and the papers cite ([provenance.md](provenance.md)),
with new package and class names. This document lists what can make results differ from the cited code: the fixes, the
defects that are still there, and the structural changes that matter for comparisons. Everything else (modules,
packages, documentation, tests, fixes that cannot change results such as a `toString`) is in the commit history and
the [release notes](https://github.com/yakupov/YB18-inds-framework/releases). Single-threaded behaviour is identical
to the cited code on all recorded scenarios, apart from the one-level case of the 2D fix below, and performance is
unchanged ([verification.md](verification.md)).

## Fixes

Each fix is a commit of its own with a test that failed before it.

**Concurrency.** Three defects found by the stress tests of this library: several threads insert disjoint parts of a
point set into one population, then every distinct point must be present once, in the level of its true rank. All
three are present in the cited versions; none shows up single-threaded.

| Population | Symptom before the fix | Present in | Fix |
|---|---|---|---|
| Fine (both versions; thesis Section 4.2.3) | a point left in the same level as a point that dominates it; 5–30 % of runs with 6 or more threads | every version since January 2018 | [`bc7bb35`](https://github.com/yakupov/YB18-inds-framework/commit/bc7bb35) |
| CAS, one-by-one insertion (time-stamping; Section 4.2.2) | rarely a misranked point: about 1 of 10 000 runs | every version | [`0b2076a`](https://github.com/yakupov/YB18-inds-framework/commit/0b2076a) |
| CAS, full sortings (`useOneByOneSorting = false`) | duplicated points, hangs, exceptions, already with 2 threads | from October 2018 (incl. the thesis version); not used in the thesis experiments | [`6582de9`](https://github.com/yakupov/YB18-inds-framework/commit/6582de9) |

- **Fine.** Inserting into level *i* evicts the points it dominates, which cascade into level *i + 1*, hand over hand.
  The cited code published the new level *i* before acquiring the lock of level *i + 1*, so evicted points were in no
  level for a moment; another thread could then put a point that dominates one of them into level *i + 1*, and the
  evicted point was added next to it unchecked. Fix: acquire lock *i + 1* first (one line moved; locks still in
  ascending order). Performance within ±4 % of the cited code at 3, 6 and 12 threads.
- **CAS, time-stamping.** An insertion that started after a level's last modification takes a fast path without
  re-checking dominance. The cited code stamped a level version before the compare-and-set that publishes it, so a
  version published after an insertion started could carry an older stamp. Fix: a second stamp taken right after
  publication, which the fast path compares with. Performance within −5 % … +3 %.
- **CAS, full sortings.** Points that a full sorting pushes to the next level must leave this one; a change of
  October 2018 passed all addends instead of the remaining ones to the level update. Invisible single-threaded (the
  starting rank is exact there). Fix: the earlier logic (pass the remaining addends).

**2D: rank of a point while there is one level**
([`6f44d41`](https://github.com/yakupov/YB18-inds-framework/commit/6f44d41)). `TwoDimTreapPopulation.detRankOfExPoint`
(used by `getRandWithRank` and `validate`) rejected rank 0 in the check after its binary search, which is reached only
when the search is skipped, i.e. with exactly one level: every call threw. Fix: accept rank 0. Results with two or more
levels are unchanged. Since then, the method is gone: the rank of a point in the population is the rank
`determineRank` computes for it.

## Known defects

Kept as they are, so that the code stays the cited code; the golden-master records record them. Each defect that
should be fixed has an issue.

| Where | Defect | Issue |
|---|---|---|
| Level, Coarse | `size()` undercounts and eviction is skipped when an insertion pushes a whole level down | [#1](https://github.com/yakupov/YB18-inds-framework/issues/1) |
| Level, Coarse, Fine | `clone()` throws (Level, Coarse) or returns a copy that shares state with the original (Fine) | [#2](https://github.com/yakupov/YB18-inds-framework/issues/2) |
| Level, Coarse, Fine, CAS | the inner members of a level whose members share one objective value get a NaN crowding distance | [#17](https://github.com/yakupov/YB18-inds-framework/issues/17) |
| CAS | retry loops swallow index errors of the sorting and crowding-distance code (endless retry); a removed last level is waited for by spinning | [#3](https://github.com/yakupov/YB18-inds-framework/issues/3) |
| CAS | the logical clock (`AtomicLong`) has no overflow handling (`todo` in the code) | none: 2⁶³ ticks are out of reach |
| Fine, CAS | `getSnapshot()` is not linearizable: points moving between levels can be missing, and `size()` can disagree with the snapshot during insertions | none: inherent in the lock / CAS design ([architecture.md](architecture.md)) |

## Structure

- One copy of the shared helpers for all algorithms, from the latest version (nds2018 r2019.2); the classes from the
  thesis version (CAS, thesis Fine) run on them. The only helper with different code is `Utils.dominates`: iterative
  with early exit instead of recursive, with identical results and up to 2× faster for 6 objectives
  ([provenance.md](provenance.md)).
- Fine exists in two versions: as in the thesis (`FineLockJfbyPopulation`) and the 2019 revision that retries from a
  rank hint (`FineLockJfbyPopulationRankHint`).
- ENLU is included as a population (`EnluPopulation`, the Chapter 4 baseline), which maintains crowding distances
  like Level, so the two compare like for like. The levels-only ENLU sorter of the Chapter 3 experiments (nds2016,
  no crowding distances) is not included; like a sorting-only Level, it would be a second version of an algorithm
  the library already has.
- 2D works on the library's `Individual` instead of its own point type (`Double2DIndividual`), which also counted
  comparisons in a static counter; this library does not count them. Points are ranked by their first two
  objectives. As in the cited code, a point equal to one in the population is not added again; points that are
  different but have equal coordinates (the cited code had no such points) share a treap node and a level. The
  insertion is the cited one; on top, the population is a `ManagedPopulation` (the cited one was insertion-only with
  `getRandWithRank`): crowding distances over the first two objectives (Level's formula, except that an objective
  without range in a level contributes 0 instead of NaN,
  [#17](https://github.com/yakupov/YB18-inds-framework/issues/17)), random solutions, snapshots, `clone`, and eviction
  above an expected size with Level's rule.
- Not ported: the NSGA-II code (it depends on the LGPL MOEA Framework), the data-set generators, vendored third-party
  code, unused experiments.
