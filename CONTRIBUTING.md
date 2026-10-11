# Contributing

Rules for changing this library, for people and coding agents alike. `CLAUDE.md` adds agent-specific notes.

## Two kinds of code

- **Ported code**: the algorithm classes copied from the legacy repositories cited in the thesis (origin per file in
  [docs/provenance.md](docs/provenance.md)). They stay byte-identical to the cited code apart from `package` / `import`
  lines and class renames, so that results remain comparable with the thesis. Do not reformat, rename members or
  "clean up" their internals. A defect is fixed only deliberately: list it as a known defect with an item of its own
  in [docs/roadmap.md](docs/roadmap.md), add a test that shows it, then fix it in a commit of its own; move the item
  to "Done and closed" and record the change in
  [docs/changes-from-cited-code.md](docs/changes-from-cited-code.md). The golden-master tests
  (`org.itmo.inds.golden`) fail on any behaviour change, so a fix also updates or replaces the affected golden records,
  explained in the same commit.
- **New code**: everything else (APIs written for this repository, data loading, tests, benchmarks). The rules below
  apply to it.

## Code quality of new code

Structure first, documentation second.

- **Domain values are types, not literals.** Kinds, labels, ids, statuses and keys are enums or named constants. A
  text form that must match a file or an external format lives in exactly one place (the enum or constant), never as
  literals scattered over the code or compared with `equals`.
- **One way to build one kind of thing.** No factory method for some cases and a constructor for others; APIs take the
  domain type, not a string that encodes it.
- **Names say what a thing is**, and a name keeps its promise (a `className` holds a class name). No vague or
  one-letter names for non-trivial fields, parameters and locals (loop indices and exception variables excepted).
- **Small methods with one purpose**; a long method becomes named steps. Prefer returning a value or an `Optional` to
  mutating a `null`-initialised local. No dead code, no unused parameters. State the threading assumption where code
  runs in parallel.
- **Results are values.** A check returns `Optional` (empty = passed) or a small record / enum saying what happened;
  never `null` for success, and never a message whose text or prefix callers parse to find out what kind of result it
  is.
- **No copies of logic, tests included.** When two classes check the same invariant or need the same helper, it lives
  once (for a helper about one class: in that class's test package) and both use it.
- **`final`**: every effectively final field and local variable is declared `final` (including enhanced-`for` loop
  variables and `try`-with-resources variables).
- **No defect-prone code**: no possible `null` dereferences, ignored results of calls that report something, unused or
  redundant code, broken Javadoc references, raw types. Static analysis and linting in `mvn verify` that check this
  automatically are planned ([docs/roadmap.md](docs/roadmap.md)); until then it is part of reviewing the diff (an
  IDE's inspections help). Findings in ported code are left alone, and code is not contorted to silence style-only
  findings (prose style, a generator parameter that happens to be constant).
- **Review your own diff** as someone new to the code before committing: string literals, vague names, two
  construction paths, long methods, comments that compensate for structure.

## Javadoc

Javadoc says what the name and the types do not: purpose, units, ranges, `null` handling, order, side effects,
threading. Types and non-trivial methods have it, with `@param` / `@return` / `@throws` where they add information;
self-explanatory constants, trivial accessors and obvious private helpers need none. Project-specific terms are defined
once, in the package's `package-info.java`, and used consistently. Documentation refers only to things in this
repository and is written for its readers: no references to how the code came about (plans, checkpoints, task or
review labels); explain the point itself, or link to the user-facing docs. Javadoc is no substitute for structure: if it has to explain a string or a muddled parameter, fix the code.
Test methods get a one-line description when their name does not say what is checked.

## Formatting

[.editorconfig](.editorconfig): IntelliJ IDEA defaults, plus braces always around `if` / `for` / `while` / `do` bodies
and no control statement on one line; lines up to 120 characters (the formatter does not wrap long lines; wrap them by
hand). Format new code with *Reformat Code* on the changed files, or with IntelliJ's command-line formatter
(`format.sh` / `format.bat`). Never reformat ported classes, also not by reformatting a whole directory. Line endings
are LF ([.gitattributes](.gitattributes)), except Windows scripts (`.cmd`, `.bat`).

## Tests

`mvn verify` runs everything and must pass: ported legacy tests, golden-master tests (bit-identical behaviour;
records specified in [docs/golden-records.md](docs/golden-records.md)),
oracle cross-checks (correctness against an independent sorter), short concurrency stress tests (long runs:
`-Pstress`). New behaviour comes with tests; a fixed defect comes with a test that failed before the fix.

- **Observe through the API.** Tests read the state of the code under test through its public API, including
  constructors that take the collections the object fills; reflection only where no API exists, in one helper per
  class.
- **Seeded inputs are part of the test.** Refactoring a generator of test data must not change a single value: compare
  a digest of everything it generates before and after. The same holds for the order in which tests consume random
  numbers.
- **Failures say where.** A failure message names the input, the step or repetition and what differs, so it can be
  reproduced without a debugger.

## Performance

Benchmark numbers are machine-specific. Before changing an algorithm, measure the relevant benchmarks of the unchanged
code on your machine, then compare with the same setup afterwards (fixed JDK, CPU pinning, an otherwise idle
machine).

## Benchmark data

`inds-benchmark-data` holds the data sets of the thesis experiments. Files are never edited in place; adding,
replacing or removing a data set updates [MANIFEST.tsv](inds-benchmark-data/MANIFEST.tsv), the module
[README](inds-benchmark-data/README.md) and `DataSetsTest` in the same commit (see "Changing the data" there).

## Where information belongs

Each kind of information has one place; other documents link to it instead of repeating it, and a document contains
what its name says.

| Information | Place |
|---|---|
| what the library is, quick start, index of the documentation | [README.md](README.md) |
| how it works: modules, packages, API, how each population works, data model, test layout | [docs/architecture.md](docs/architecture.md) |
| open work: the known defects of the cited code (each with its item or the reason it has none) and planned or possible improvements, one item per piece of work, with a stable ID (F1, F2, …) and a gate; finished items under "Done and closed" | [docs/roadmap.md](docs/roadmap.md) |
| every way the code differs from the cited code (fixes with their diffs, structural changes), nothing that is still open | [docs/changes-from-cited-code.md](docs/changes-from-cited-code.md) |
| how sameness with the cited code is checked, and the results | [docs/verification.md](docs/verification.md) |
| running the benchmarks: parameters, examples, comparable numbers | [inds-benchmarks/README.md](inds-benchmarks/README.md) |
| which thesis experiment uses which data and benchmark, and how to re-run it | [docs/reproducibility.md](docs/reproducibility.md) |
| origin of every ported file | [docs/provenance.md](docs/provenance.md) |
| format of the golden-master records | [docs/golden-records.md](docs/golden-records.md) |
| the data sets: list, format, sources, how to change them | [inds-benchmark-data/README.md](inds-benchmark-data/README.md), `MANIFEST.tsv` |
| terms and thesis labels of a package | its `package-info.java` |
| the contract of a type or method | its Javadoc |
| rules for changing the code | this file; agent-specific notes in `CLAUDE.md` |

Typical updates: a defect found in ported code → a row in the roadmap's known-defects table and an item; the defect
fixed → the item moves to "Done and closed", the fix gets an entry in `changes-from-cited-code.md`, and
`verification.md` changes if the checks or their results do.

## Licenses

MIT only: no source derived from LGPL / GPL code (e.g. MOEA Framework) and no third-party code without a clear
license; third-party libraries only as Maven dependencies with MIT-compatible licenses.
