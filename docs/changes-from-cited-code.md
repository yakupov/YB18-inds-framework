# Changes from the cited code

What can make results of this library differ from the code the thesis cites ([provenance.md](provenance.md)): fixes,
known limitations and structural differences. Everything else is in the commit history and the
[release notes](https://github.com/yakupov/YB18-inds-framework/releases); performance: [verification.md](verification.md).

## Fixes

Each fix is a commit of its own with a test that failed before it. The golden-master blocks a fix changes are listed in
`golden-divergences.txt` ([golden-records.md](golden-records.md#changing-the-records)).

| Where | Symptom in the cited code | Fix | Commit / issue |
|---|---|---|---|
| Fine, both versions (since January 2018) | concurrent insertions: a point left in the level of a point that dominates it, in 5–30 % of runs with 6 or more threads | lock level *i + 1* before publishing level *i* | [`bc7bb35`](https://github.com/yakupov/YB18-inds-framework/commit/bc7bb35) |
| CAS, one-by-one insertion (all versions) | concurrent insertions: rarely a misranked point, about 1 in 10 000 runs | the fast path compares with a time stamp taken after publication | [`0b2076a`](https://github.com/yakupov/YB18-inds-framework/commit/0b2076a) |
| CAS, full sortings (since October 2018; not used in the thesis experiments) | concurrent insertions: duplicated points, hangs, exceptions | pass only the remaining addends to the level update, as before October 2018 | [`6582de9`](https://github.com/yakupov/YB18-inds-framework/commit/6582de9) |
| 2D | rank queries threw while the population had one level | accept rank 0 | [`6f44d41`](https://github.com/yakupov/YB18-inds-framework/commit/6f44d41) |
| Level, Coarse | an insertion that pushes a whole level down was not counted and skipped eviction: `size()` too small, the population above its expected size, `toRankedPopulation()` threw | count it and check eviction like any insertion | [#1](https://github.com/yakupov/YB18-inds-framework/issues/1) |
| Level, Coarse, Fine | `clone()` threw (Level, Coarse) or returned a copy sharing the level list with the original (Fine; inserting into it could hang) | copy the levels into a list of the copy's own; Coarse returns a Coarse | [#2](https://github.com/yakupov/YB18-inds-framework/issues/2) |
| Level, Coarse, Fine, CAS, ENLU | members of a level sharing one objective value got crowding distance NaN (never evicted, broke comparisons) | an objective without range adds 0, as in 2D | [#17](https://github.com/yakupov/YB18-inds-framework/issues/17) |
| CAS | `size()` counted insertions, not members, once eviction started | decrement it on eviction | [#18](https://github.com/yakupov/YB18-inds-framework/issues/18) |
| Fine, both versions | concurrent insertions with eviction: a point placed too deep because its rank, computed without locks, went stale; sometimes in the level of a point that dominates it (up to 7 % of the stress runs) | an eviction counter; if it changed, check under the lock that the level before the rank dominates the point | [#20](https://github.com/yakupov/YB18-inds-framework/issues/20) |
| CAS, both modes | the same (up to 1.3 % of the stress runs), and eviction of a member from a level that already had a successor | levels are sealed before one is added behind them and only the unsealed last level is evicted; the same counter and check, points that fail it are inserted again | [#20](https://github.com/yakupov/YB18-inds-framework/issues/20) |
| CAS | `clone()` threw a `NullPointerException` if another thread removed the last level meanwhile | skip the reference of a level being removed, as `getSnapshot()` does | [#19](https://github.com/yakupov/YB18-inds-framework/issues/19) |

## Known limitations

Defects that are not fixed yet are GitHub issues labelled `known-defect`.

| Where | Limitation | Why it stays |
|---|---|---|
| Fine, CAS | the counters (the CAS clock, the eviction counters; 64 bits) are never reset | 2⁶³ increments are out of reach; the eviction counters are only compared for inequality |
| Fine, CAS | `getSnapshot()` is not linearizable: during insertions, moving points can be missing and `size()` can disagree with the snapshot | inherent in the design ([architecture.md](architecture.md)) |

Where the members of a level share a value in an objective, their order in the crowding-distance computation, and so
which of them count as extremes or neighbours, depends on the insertion order. The cited work does not specify it;
this library keeps it.

## Structure

- Shared helpers exist once, from nds2018 r2019.2. Only `Utils.dominates` differs from the thesis version: iterative
  instead of recursive, same results, up to 2× faster for 6 objectives.
- Fine exists in two versions: the thesis one (`FineLockJfbyPopulation`) and the 2019 revision that retries from a rank
  hint (`FineLockJfbyPopulationRankHint`).
- ENLU is included as a population with crowding distances (`EnluPopulation`, the Chapter 4 baseline); the levels-only
  ENLU sorter of Chapter 3 is not.
- 2D works on the library's `Individual`, ranked by the first two objectives, without the static comparison counter;
  points with equal coordinates share a level. On top of the cited insertion it is a full `ManagedPopulation`:
  crowding distances over the first two objectives, eviction by Level's rule, snapshots, `clone`, random solutions.
- Not ported: the NSGA-II code (it depends on the LGPL MOEA Framework), the data-set generators, vendored third-party
  code, unused experiments.
