# Changes from the cited code

The algorithm classes of this library are the code the thesis and the papers cite ([provenance.md](provenance.md)),
with new package and class names. Their logic is unchanged except for **three concurrency fixes**, each in a commit
of its own. Everything else that differs from the cited code is structure: modules, packages, class names, a common
version of the shared helpers, documentation and tests. Single-threaded behaviour is identical to the cited code on
all recorded scenarios, and performance is unchanged ([verification.md](verification.md)).

This document lists only what differs from the cited code. Defects of the cited code that are not fixed are open
work and listed in the [roadmap](roadmap.md#known-defects-of-the-cited-code); when one is fixed, it gets an entry here.

## Fixes

The three defects were found by the concurrency stress tests written for this library: several threads insert
disjoint parts of a point set into one population, then every distinct point must be present once, in the level of
its true rank. All three are also present in the cited versions; none shows up single-threaded.

| Population | Symptom before the fix | Present in |
|---|---|---|
| Fine (both versions) | a point left in the same level as a point that dominates it; 5–30 % of runs with 6 or more threads | every version since January 2018 |
| CAS, time-stamping (one-by-one insertion) | rarely a misranked point: about 1 of 10 000 runs | every version |
| CAS, full sortings (`useOneByOneSorting = false`) | duplicated points, hangs, exceptions, already with 2 threads | from October 2018 (incl. the thesis version); not used in the thesis experiments |

### Fine: acquire the next level's lock before publishing a level

`FineLockJfbyPopulation`, `FineLockJfbyPopulationRankHint` (thesis Section 4.2.3).

Inserting a point into level *i* evicts the points it dominates; they cascade into level *i + 1*, hand over hand.
The cited code published the new level *i* and only then acquired the lock of level *i + 1*. In between, the evicted
points were in no level. Another thread could insert into level *i + 1* a point *p* that dominates an evicted point
*e*; when *e* then arrived, `addMembers` added it without checking whether a member dominates it (its precondition is
that cascaded points are not dominated by the next level, which holds only while no point is in transit). *e* stayed
in level *i + 1* next to *p*.

The fix moves one line: the lock of level *i + 1* is acquired first, so a thread that ranks its point against the new
level *i* can enter level *i + 1* only after the evicted points are there. Locks are still taken in ascending order
(no new deadlock). The same change in both versions:

```diff
                     final NonDominationLevel.MemberAdditionResult<T, JfbyNonDominationLevel<T>> memberAdditionResult = level.addMembers(addends);
-                    nonDominationLevels.set(i, memberAdditionResult.getModifiedLevel());
 
                     if (!memberAdditionResult.getEvictedMembers().isEmpty()) {
                         acquireLock(i + 1);
                     }
+                    nonDominationLevels.set(i, memberAdditionResult.getModifiedLevel());
                     addends = memberAdditionResult.getEvictedMembers();
```

Readers (`getSnapshot`) see the old level *i* a little longer. Performance: within ±4 % of the cited code at 3, 6 and
12 threads.

### CAS: time-stamp a level version after it is published

`CasJfbyPopulation`, one-by-one insertion (thesis Section 4.2.2).

Each level version carries a time stamp; an insertion that started after the level's last modification takes a fast
path that adds its points without re-checking dominance. The cited code stamped a version (`time.incrementAndGet()`)
*before* the compare-and-set that publishes it, so a version published after an insertion started could carry an
older stamp, and the fast path then trusted a level that had changed. The fix gives every version a second stamp,
`publishedTs`, taken right after its publishing compare-and-set (`Long.MAX_VALUE` until then), and the fast path
compares with it. Abridged:

```diff
     private class LevelRef {
         final long modificationTs;
         final JfbyNonDominationLevel<T> level;
+        volatile long publishedTs = Long.MAX_VALUE;
 …
+    private boolean casPublish(AtomicReference<LevelRef> ref, LevelRef expected, LevelRef update) {
+        if (ref.compareAndSet(expected, update)) {
+            update.published(time.incrementAndGet());
+            return true;
+        }
+        return false;
+    }
 …
-                    if (ts > levelRef.modificationTs) {
+                    if (ts > levelRef.publishedTs) {
 …
-                        if (nonDominationLevels.get(rank).compareAndSet(levelRef, new LevelRef(time.incrementAndGet(), mar.getModifiedLevel()))) {
+                        if (casPublish(nonDominationLevels.get(rank), levelRef, new LevelRef(time.incrementAndGet(), mar.getModifiedLevel()))) {
```

Every other publication of a level version goes through `casPublish` (or sets the stamp right after adding the
level). Performance: within −5 % … +3 % of the cited code.

### CAS: keep only the non-dominated points in a level (full sortings)

`CasJfbyPopulation` with `useOneByOneSorting = false`.

When several points are added to a level by a full sorting, those that the sorting pushes to the next level must not
stay in this one. A change of October 2018 (included in the thesis version) passed all addends instead of the
remaining ones to the level update. Single-threaded the starting rank is exact, so no addend is ever pushed and the
defect is invisible; concurrently it duplicated points and corrupted levels, which the retry loop then turned into
hangs or escaping `IndexOutOfBoundsException`s. The fix restores the earlier logic:

```diff
                     final List<Individual<T>> addendsList = new ArrayList<>(addendsSet.size());
-                    addendsList.addAll(addends);
+                    addendsList.addAll(addendsSet);
                     addendsList.sort(AscLexSortComparator.getInstance());
```

The thesis experiments used the one-by-one variant, which was not affected.

### Benchmarks: measure compiled code

The single-point insertion benchmarks behind the thesis Chapter 3 figures (nds2016 `PPSN2016Test`) rebuilt the
structures of all algorithms for all ten generations before every measured insertion (about 0.5 s), so a fork ran
the measured insertion only 40–70 times, below the JIT compiler's threshold: the ENLU sorter and Level were measured
as interpreted code (only Level's inner dominance loop got compiled), the 2D treap as compiled code, because the setup
itself inserts its points (observed with `-XX:+PrintCompilation`). This library's benchmarks rebuild only the measured
structure and average thousands of insertions per iteration, so every algorithm is measured compiled
([reproducibility.md](reproducibility.md#limitations)).

## Structure

- Modules, packages and class names: [architecture.md](architecture.md); original names: [provenance.md](provenance.md).
- One copy of the shared helpers for all algorithms, from the latest version (nds2018 r2019.2); the classes from the
  thesis version (CAS, thesis Fine) run on them. The only helper with different code is `Utils.dominates`: iterative
  with early exit instead of recursive, with identical results.
- Fine exists in two versions: as in the thesis (`FineLockJfbyPopulation`) and the 2019 revision that retries from a
  rank hint (`FineLockJfbyPopulationRankHint`).
- `FitnessAndCdIndividual` had one cosmetic edit after the port: the import of `Individual` (same package) is gone,
  and `equals` declares its cast local as `final var`. No change in behaviour.
- ENLU is included as a population (`EnluPopulation`, the Chapter 4 baseline), which maintains crowding distances
  like Level, so the two compare like for like. The levels-only ENLU sorter of the Chapter 3 experiments (nds2016,
  no crowding distances) is not included; like a sorting-only Level, it would be a second version of an algorithm
  the library already has.
- Not ported: the NSGA-II code (it depends on the LGPL MOEA Framework), the data-set generators, vendored third-party
  code, unused experiments.
