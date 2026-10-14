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
- **Ported code changes only with the maintainer's approval**: implement the change, show the diff of the ported
  files, and commit only after the maintainer has verified it. Nothing on the side: no member renames, no "cleanups"
  of internals (loops, data structures, array-vs-list choices, sweep / merge logic, crowding-distance math), no
  reformatting; it is performance-tuned research code whose results must stay comparable with the thesis. Report a
  suspected defect first; fix it with the steps in CONTRIBUTING.md. Known defects are issues labelled `known-defect`
  (`gh issue list`); known limitations:
  [docs/changes-from-cited-code.md](docs/changes-from-cited-code.md#known-limitations).
- **Concurrent classes** (`FineLockJfbyPopulation*`, `CasJfbyPopulation`, `CoarseLockJfbyPopulation`,
  `EnluPopulation`): not even "harmless" edits (`final`, diamonds, lambdas, collection-type swaps, annotation or comment
  changes, replacing `synchronized` / locks / atomics) unless approved as above; every change to them is checked with
  `mvn verify -Pstress` and a performance comparison.
- Thesis labels (DC, DC1, DC2, Level, Coarse, Fine, CAS, ENLU, 2D) go into `package-info.java` and docs, not into
  ported classes.
- **MIT only:** no source derived from LGPL / GPL code (e.g. MOEA Framework), no third-party code without a clear
  license. M. Buzdalov's MIT library (`com.github.mbuzdalov:non-dominated-sorting-implementations`) is the test oracle;
  its package root `ru.ifmo.nds` is also the root of the legacy nds2018 code, so never put both on one classpath.
- **Ask first** before changing ported code, changing benchmark data, or pushing.

## Checking your work

Run `mvn verify` after every change (all test layers, [docs/verification.md](docs/verification.md); the authoritative
check), and `mvn verify -Pstress` after touching a concurrent class. Review the diff as CONTRIBUTING.md asks. Do not
rely on headless IntelliJ inspections (`inspect.sh` / `inspect.bat`): they can analyse before indexing has finished and
then report a stale state or nothing. Static analysis in the build is planned
([#9](https://github.com/yakupov/YB18-inds-framework/issues/9),
[#10](https://github.com/yakupov/YB18-inds-framework/issues/10)).

## Performance work

Measure the unchanged and the changed code on the same machine with the same setup
([inds-benchmarks/README.md](inds-benchmarks/README.md#comparable-numbers)); never compare with numbers from another
machine. Gate: at most 5 % slower or overlapping 99.9 % confidence intervals. Re-run a flagged result once, if
possible in the reverse order: a single run can be off by 20 % on this kind of benchmark.

## Layout

| Module | Contents |
|---|---|
| `inds-core` | `api`, `sorting` (DC / DC1 / DC2), `population` (Level, Coarse, Fine ×2, CAS), `twod` (2D), `util`; `package-info` files map classes to thesis labels and papers |
| `inds-baselines` | ENLU population |
| `inds-testdata` | data-set loader (`DataSets`, `FrontsAndAddends`) and the original Gson model |
| `inds-benchmark-data` | JSON data sets, resources only (~1.9 GB) |
| `inds-benchmarks` | JMH benchmarks → `target/benchmarks.jar` |

## Commands

```bash
mvn verify                                   # all modules, all tests
mvn -pl inds-core test                       # one module
mvn -pl inds-core test -Dtest=OracleTest     # one test class (JUnit 5; JUnit 4 tests run via the vintage engine)
mvn -pl inds-core,inds-baselines verify -Pstress   # long concurrency stress runs (~15 min)
```

## Gotchas

- Never read, grep or glob into `inds-benchmark-data/**` or the `*.gz` files in
  `inds-core/src/test/resources/org/itmo/inds/golden/` (large data); listing names and sizes is fine.
- The field names of the data model classes (`FrontStorage`, `RunConfiguration`, `Generation` / `DoublesGeneration`,
  `Front`, `DoublesAdditionProblem`) are the JSON format of the data sets: renaming one breaks loading unless
  `@SerializedName` keeps the old name.
- Golden records are outputs of the cited code and never change; a deliberate behaviour change lists its blocks in
  `golden-divergences.txt` ([docs/golden-records.md](docs/golden-records.md)).
- Shared test infrastructure lives in `inds-core`'s test sources and reaches the other modules through its test-jar:
  golden (`GoldenCheck`, `Replays`, `Target`; vocabulary in the `org.itmo.inds.golden` package-info), oracle
  (`OracleInputs`, `ReferencePopulation` / `ModelCrossCheck`, `PopulationOracle`, `PopulationInvariants`), stress
  (`AbstractConcurrentInsertionStressTest`), 2D (`TwoDimTreapTraversal`). Same package names in the other module give
  access to package-private helpers. Tests reach the levels of `TwoDimTreapPopulation` by passing their own list to
  its `(Set, List)` constructor, not by reflection.
