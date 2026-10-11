# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

MIT-licensed Java 17 library of incremental and asynchronous non-dominated sorting (INDS): the algorithms of
I. Yakupov's PhD thesis (ITMO University, 2019), ported from the research repositories the thesis cites, plus
baselines, benchmark data and benchmarks. Start with [README.md](README.md) and [docs/architecture.md](docs/architecture.md).

## Rules

- **Code rules: [CONTRIBUTING.md](CONTRIBUTING.md).** Read it before writing code. It separates *ported code* (the
  algorithm classes, origin per file in [docs/provenance.md](docs/provenance.md)) from *new code*, sets the quality,
  Javadoc, formatting and test rules for new code, and says which document holds which information ("Where
  information belongs"): keep each fact in its one place when you update docs.
- **Ported code stays the cited code.** No member renames, no "cleanups" of internals (loops, data structures,
  array-vs-list choices, sweep / merge logic, crowding-distance math), no reformatting: it is performance-tuned research
  code, and results must stay comparable with the thesis. A suspected defect is reported first; a fix needs the
  maintainer's approval and comes as CONTRIBUTING.md describes (a test that shows it, a commit of its own, an entry in
  [docs/changes-from-cited-code.md](docs/changes-from-cited-code.md), golden records updated in the same commit).
  Known defects and open work: [docs/roadmap.md](docs/roadmap.md).
- **Concurrent classes** (`FineLockJfbyPopulation*`, `CasJfbyPopulation`, `CoarseLockJfbyPopulation`,
  `AbstractConcurrentJfbyPopulation`, `EnluPopulation`): not even "harmless" edits (`final`, diamonds, lambdas,
  collection-type swaps, annotation or comment changes, replacing `synchronized` / locks / atomics). Any change to them
  is a fix as above, checked with `mvn verify -Pstress` and a performance comparison.
- Thesis labels (DC, DC1, DC2, Level, Coarse, Fine, CAS, ENLU, 2D) go into `package-info.java` and docs, not into
  ported classes.
- **MIT only:** no source derived from LGPL / GPL code (e.g. MOEA Framework), no third-party code without a clear
  license. M. Buzdalov's MIT library (`com.github.mbuzdalov:non-dominated-sorting-implementations`) is the test oracle;
  its package root `ru.ifmo.nds` is also the root of the legacy nds2018 code, so never put both on one classpath.
- **Ask first** before changing ported code, changing benchmark data, or pushing.

## Checking your work

Run `mvn verify` after every change (legacy unit, golden-master, oracle and short stress tests; the authoritative
check), and `mvn verify -Pstress` after touching a concurrent class. Review the diff as CONTRIBUTING.md asks. Do not
rely on headless IntelliJ inspections (`inspect.sh` / `inspect.bat`): they can analyse before indexing has finished
and then report a stale state or nothing. Static analysis in the build is planned ([docs/roadmap.md](docs/roadmap.md)).

## Performance work

Benchmark numbers are machine-specific; never compare with numbers from another machine. Before changing an algorithm,
measure the relevant benchmarks (`inds-benchmarks`) of the unchanged code on your machine, then the changed code with
the same setup: fixed JDK, CPU affinity pinned (e.g. to the cores of one CCX), idle machine, several forks. A
reasonable gate: at most 5 % slower or overlapping 99.9 % confidence intervals; re-run flagged results once, and if
the unchanged code has drifted too, compare both from the same day.

## Layout

| Module | Contents |
|---|---|
| `inds-core` | `api`, `sorting` (DC / DC1 / DC2), `population` (Level, Coarse, Fine ×2, CAS), `twod` (2D), `util`; `package-info` files map classes to thesis labels and papers |
| `inds-baselines` | ENLU population |
| `inds-testdata` | data-set loader (`DataSets`, `FrontsAndAddends`) and the original Gson model |
| `inds-benchmark-data` | JSON data sets, resources only (~1 GB) |
| `inds-benchmarks` | JMH benchmarks → `target/benchmarks.jar` |

## Commands

```bash
mvn verify                                   # all modules, all tests
mvn -pl inds-core test                       # one module
mvn -pl inds-core test -Dtest=OracleTest     # one test class (JUnit 5; JUnit 4 tests run via the vintage engine)
mvn -pl inds-core,inds-baselines verify -Pstress   # long concurrency stress runs (~15 min)
```

## Gotchas

- Never read, grep or glob into `inds-benchmark-data/**` or `inds-core/src/test/resources/org/itmo/inds/golden/**`
  (large data); listing names and sizes is fine.
- The field names of the data model classes (`FrontStorage`, `RunConfiguration`, `Generation` / `DoublesGeneration`,
  `Front`, `DoublesAdditionProblem`) are the JSON format of the data sets: renaming one breaks loading unless
  `@SerializedName` keeps the old name. Gson cannot reflect into `java.time` on JDK 17; `DataSets` registers
  `LocalDateTimeAdapter`.
- Golden records are outputs of the cited code and change only with a deliberate behaviour change
  ([docs/golden-records.md](docs/golden-records.md)).
- Shared test infrastructure lives in `inds-core`'s test sources and reaches the other modules through its test-jar:
  golden (`GoldenCheck`, `Replays`, `Target`; vocabulary in the `org.itmo.inds.golden` package-info), oracle
  (`OracleInputs`, `PopulationOracle`, `PopulationInvariants`, shared with the stress test), stress
  (`AbstractConcurrentInsertionStressTest`), 2D (`TwoDimTreapTraversal`). Same package names in the other module give
  access to package-private helpers. Tests reach the levels of `TwoDimTreapPopulation` by passing their own list to
  its `(Set, List)` constructor, not by reflection.
