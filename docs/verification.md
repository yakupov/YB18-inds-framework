# Verification

How this library was checked to be the code the thesis cites: the same logic (apart from the fixes in
[changes-from-cited-code.md](changes-from-cited-code.md)), the same behaviour and the same performance. The checks
in `mvn verify` keep protecting it.

## 1. The code is a copy

Every ported class entered the repository in two steps, each a commit of its own (kept in the history of `main`):

1. **Verbatim copy** of the legacy file, with only the `package` and `import` lines changed; the commit message
   names the legacy repository, ref and commit ([provenance.md](provenance.md)).
2. **Class renames** to the names of this library (e.g. `JFB2014` → `JfbSorter`). Checked mechanically: undoing the
   renames on the result gives the copied files back byte for byte.

Algorithm internals were not reformatted, re-indented or otherwise edited; the only other edit of a ported class is
cosmetic: `FitnessAndCdIndividual` lost an import of its own package, and `equals` declares its cast local as
`final var`. The three
concurrency fixes came later, one commit each; the stress tests that found them run in `mvn verify`. Small fixes of
the 2D population followed, each in a commit of its own with a test.

## 2. Tests in `mvn verify`

| Layer | What it catches | Tests |
|---|---|---|
| Legacy unit tests | gross breakage | the ported nds2018 tests (`*CorrectnessTest`) and the nds2016 sorter tests (`*Nds2016Test`) |
| Golden master | any change of observable behaviour against the cited code | `GoldenMasterTest` (inds-core), `EnluGoldenMasterTest` (inds-baselines) |
| Oracle cross-check | wrong results, independently of the cited code | `OracleCrossCheckTest`, `TwoDimTreapPopulationOracleTest`, `EnluOracleCrossCheckTest` |
| Concurrency stress | races in the asynchronous populations | `ConcurrentInsertionStressTest`, `EnluConcurrentInsertionStressTest` |
| Known defects | that documented defects of the cited code are still there, unchanged | `TwoDimTreapPopulationKnownBugsTest` |

A full `mvn verify` runs about 780 tests in a few minutes.

### Golden master

The legacy builds of the cited code (nds2018 `r2019.2`, nds2018 `r2018.9.1-cd1`, nds2016 `master`) were run once, by
a recorder, on 48 seeded input sets: uniform random points (2 to 10 objectives, up to 10 000 points), synthetic
shapes with few large levels, degenerate sets (all points equal, many duplicates, ties, a single point) and NSGA-II
populations from the benchmark data. Scenarios cover every public operation of every algorithm: full and
incremental sorting, population insertion with an unlimited and with a small expected size (so eviction runs),
snapshots after every step with levels, members, objectives and crowding distances (doubles as exact bit
patterns), rank queries, `clone()`, and the 2D API. The tests replay the same scenarios on the ported
classes and compare the records line by line; record format: [golden-records.md](golden-records.md).

All records are identical, including the outcomes of the [known defects](changes-from-cited-code.md#known-defects)
(e.g. the exception of `clone()`). The two nds2018 refs also agree with each other on every class they share, and the
thesis and 2019 versions of Fine give identical records single-threaded. The fixes did not change a single record:
none of the three defects shows up single-threaded.

### Oracle cross-check

Checks correctness instead of sameness: the ranks of every algorithm are compared with an independent
implementation, M. Buzdalov's [non-dominated-sorting](https://github.com/mbuzdalov/non-dominated-sorting) library
(MIT; test dependency only). Inputs: seeded sets with 2, 3, 5 and 8 objectives, uniform random, duplicates, ties,
all-equal points, dominance chains in both orders, points on one or two simplices. Every population is checked after
each insertion (sets up to 100 points) or after every 50th and the last: the returned rank, every distinct point
present once, with its payload and unchanged objectives, in the level of its true rank. DC1 / DC2 are checked the same
way, DC on the full sets, the 2D population on its own API. No mismatch.

### Concurrency stress

Several threads (2, 6, 12, 24) insert disjoint parts of a seeded, shuffled point set (2–5 objectives, 10 %
duplicates, 10 % ties) into one population. Afterwards every distinct point must be present once, in the level of
its true rank, all levels non-dominated, and `size()` consistent. `mvn verify` runs 100 repetitions per population and
thread count; `mvn verify -Pstress` runs 10 000 (about 15 minutes). A failure names the repetition, whose data and
split can be reproduced.

The same test, run on the cited builds and on 34 historical builds of nds2018, found the three defects that are now
fixed (and when each was introduced); Coarse passes on every build. After the fixes every population passes the long run (5 populations × 4 thread counts × 10 000
repetitions, no failure). Not covered yet: eviction during concurrent insertion ([#6](https://github.com/yakupov/YB18-inds-framework/issues/6)).

## 3. Performance

JMH benchmarks of the thesis experiments were run on the legacy builds before the port and on this library
afterwards, with the same benchmark code (only the adapter to the library differs), the same JDK and the same machine
(AMD Ryzen 9 7900X3D, JDK 17.0.10, pinned to 6 cores with SMT, idle; JMH average time, 12 × 1 s warm-up, 4 × 1 s
measurement, 3 forks). Tolerance: at most 5 % slower, or overlapping 99.9 % confidence intervals; flagged results
were re-run once, and a same-day run of the legacy build separated drift of the machine from changes of the code.

| Benchmark | Algorithms | Result |
|---|---|---|
| Concurrent insertion of 1 001 points into 5 000 (DTLZ1, 3 objectives; 3, 6, 12 threads) | Fine (both), CAS (both variants), Coarse | 15 / 15 within tolerance; new / legacy 0.95–1.04 |
| Single-point insertion into NSGA-II snapshots and synthetic sets (thesis Ch. 3) | DC, DC1, DC2, Level | 47 / 48 within tolerance after one re-run; the remaining one is drift of the old measurement (the same-day legacy run moved the same way; new / legacy that day within ±4 %). Geometric means 0.99–1.03 |
| Same | ENLU population | 12 / 12 after one re-run |
| Single-point insertion, ZDT1 (2 objectives) | 2D | 3 / 3 |

So the fixes cost no measurable performance.

The benchmark module (`inds-benchmarks`) was checked against the same measurements of the cited code: it builds
bit-identical inputs, and 79 of 81 results are within tolerance. The other two are drift of the old measurement (the
benchmarks of the new and the cited code agree when run on the same day). The numbers are specific to that machine;
to compare a change of your own, measure before and after on yours (CONTRIBUTING.md, "Performance").
