# Verification

How this library was checked to be the code the thesis cites (same logic, behaviour and performance, apart from the
[fixes](changes-from-cited-code.md)), and which checks in `mvn verify` keep protecting it.

## 1. The code is a copy

Every ported class entered the repository in two commits: a verbatim copy of the legacy file with only `package` and
`import` lines changed (the message names repository, ref and commit, [provenance.md](provenance.md)), then the class
renames, checked mechanically (undoing them gives the copied file back byte for byte). Later changes of ported code are
the fixes, one commit each, and a cosmetic edit of `FitnessAndCdIndividual` (an import, a `final var`).

## 2. Tests in `mvn verify`

| Layer | What it guarantees | Tests |
|---|---|---|
| Legacy unit tests | the ported tests of the cited code pass | `*CorrectnessTest`, `*Nds2016Test` |
| Golden master | behaviour equals the cited code bit for bit, except the blocks listed per fix in `golden-divergences.txt` | `GoldenMasterTest`, `EnluGoldenMasterTest` |
| Oracle cross-check | ranks agree with an independent implementation | `OracleCrossCheckTest`, `TwoDimTreapPopulationOracleTest` |
| Reference model | levels, crowding distances, eviction and sizes of the populations follow the rules in `ReferencePopulation` | `ModelCrossCheckTest`, `EnluModelCrossCheckTest` |
| Concurrency stress | concurrent insertions leave every point once, in the level of its true rank | `ConcurrentInsertionStressTest`, `EnluConcurrentInsertionStressTest` |

- **Golden master.** The cited builds were run once on 48 seeded input sets (random, synthetic, degenerate, NSGA-II
  snapshots) through every public operation; the tests replay the same operations and compare the records line by
  line ([golden-records.md](golden-records.md)).
- **Oracle.** M. Buzdalov's [non-dominated-sorting](https://github.com/mbuzdalov/non-dominated-sorting) library (MIT,
  test dependency only) on seeded inputs with 2–8 objectives, including duplicates, ties and dominance chains.
- **Reference model.** The same inputs, with an unlimited and with small expected sizes, inserted into each population
  and into the model; ranks, evictions, levels, crowding distances (bit for bit) and sizes must agree.
- **Stress.** 2, 6, 12 and 24 threads insert disjoint parts of a shuffled point set; 100 repetitions per case in
  `mvn verify`, 10 000 with `-Pstress` (about 15 minutes). Run on the cited builds, it found the three concurrency
  defects that are now fixed. A second scenario inserts concurrently into populations with expected sizes 5 and 50
  (20 repetitions per case, 1 000 with `-Pstress`) and checks, for every population alike, what holds in every
  interleaving: no hang or exception, remaining points are inserted ones and present once, non-dominated levels, the
  level of each point is its rank among the remaining points, consistent size, size within the expected-size bounds.
  Crowding distances of the evicted points are not checked there. Run on the cited Fine code, it found the defect in
  [changes-from-cited-code.md](changes-from-cited-code.md#fixes).

## 3. Performance

JMH on the legacy builds before the port and on this library afterwards: same benchmark code, JDK and machine (AMD Ryzen
9 7900X3D, JDK 17.0.10, pinned to 6 cores with SMT, idle; average time, 12 × 1 s warm-up, 4 × 1 s measurement, 3
forks). Tolerance: at most 5 % slower or overlapping 99.9 % confidence intervals; flagged results were re-run once, with
a same-day run of the legacy build to separate machine drift from code changes.

| Benchmark | Algorithms | Result |
|---|---|---|
| Concurrent insertion of 1 001 points into 5 000 (DTLZ1, 3 objectives; 3, 6, 12 threads) | Fine (both), CAS (both variants), Coarse | 15 / 15 within tolerance; new / legacy 0.95–1.04 |
| Single-point insertion into NSGA-II snapshots and synthetic sets (thesis Ch. 3) | DC, DC1, DC2, Level | 47 / 48; the remaining one is drift (the same-day legacy run moved the same way) |
| Same | ENLU population | 12 / 12 |
| Single-point insertion, ZDT1 (2 objectives) | 2D | 3 / 3 |

The benchmark module (`inds-benchmarks`) builds bit-identical inputs and agrees with the measurements of the cited
code (79 of 81 within tolerance, the other two drift). The numbers are specific to that machine; to check a change,
measure before and after on yours (CONTRIBUTING.md, "Performance").
