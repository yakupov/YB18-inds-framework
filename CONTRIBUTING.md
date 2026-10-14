# Contributing

Rules for changing this library, for people and coding agents alike. `CLAUDE.md` adds agent-specific notes.

## Two kinds of code

- **Ported code**: the algorithm classes copied from the cited repositories (origin per file in
  [docs/provenance.md](docs/provenance.md)), byte-identical apart from `package` / `import` lines and class renames,
  so that results stay comparable with the thesis. Any change to it needs the maintainer's explicit approval after
  reviewing its diff, and nothing changes on the side of other work (no reformatting, renames or "simplifications" of
  performance-tuned code). A refactoring keeps the golden-master tests passing unchanged and the performance unchanged
  (JMH before and after).
- **New code**: everything else (APIs written for this repository, data loading, tests, benchmarks). The rules below
  apply to it.

A defect of ported code is fixed in these steps:

1. An issue (label `known-defect`).
2. A test that fails without the fix.
3. The fix, approved by the maintainer, in a commit of its own whose message says `Fixes #N`.
4. In the same commit: the golden blocks it changes listed in `golden-divergences.txt`
   ([docs/golden-records.md](docs/golden-records.md#changing-the-records); the records themselves never change), the
   reference model (`ReferencePopulation`) changed if a rule changes, and a row in the fixes table of
   [docs/changes-from-cited-code.md](docs/changes-from-cited-code.md) (none if the fix cannot change results).

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
  automatically are planned ([#9](https://github.com/yakupov/YB18-inds-framework/issues/9),
  [#10](https://github.com/yakupov/YB18-inds-framework/issues/10)); until then it is part of reviewing the diff (an
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
review labels); explain the point itself, or link to the user-facing docs. Javadoc is no substitute for structure: if
it has to explain a string or a muddled parameter, fix the code. Test methods get a one-line description when their
name does not say what is checked.

## Formatting

[.editorconfig](.editorconfig): IntelliJ IDEA defaults, plus braces always around `if` / `for` / `while` / `do` bodies
and no control statement on one line; lines up to 120 characters (the formatter does not wrap long lines; wrap them by
hand). Format new code with *Reformat Code* on the changed files, or with IntelliJ's command-line formatter
(`format.sh` / `format.bat`). Never reformat ported classes, also not by reformatting a whole directory. Line endings
are LF ([.gitattributes](.gitattributes)), except Windows scripts (`.cmd`, `.bat`).

## Tests

`mvn verify` runs everything and must pass: ported legacy tests, golden-master tests (bit-identical behaviour;
records specified in [docs/golden-records.md](docs/golden-records.md)), oracle cross-checks (correctness against an
independent sorter), reference-model checks of the populations, short concurrency stress tests (long runs:
`-Pstress`). New behaviour comes with tests; a fixed defect comes with a test that failed before the fix.
[CI](.github/workflows/ci.yml) runs `mvn verify` on every push to `main` and every pull request; the long stress
runs and the benchmarks are run by hand.

- **Observe through the API.** Tests read the state of the code under test through its public API, including
  constructors that take the collections the object fills; reflection only where no API exists, in one helper per
  class.
- **Seeded inputs are part of the test.** Refactoring a generator of test data must not change a single value: compare
  a digest of everything it generates before and after. The same holds for the order in which tests consume random
  numbers.
- **Failures say where.** A failure message names the input, the step or repetition and what differs, so it can be
  reproduced without a debugger.

## Performance

Before changing an algorithm, measure the relevant benchmarks of the unchanged code on your machine, then compare with
the same setup afterwards ([inds-benchmarks/README.md](inds-benchmarks/README.md#comparable-numbers)).

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
| open work: defects to fix, improvements, ideas; one issue per piece of work, with its gate (the checks it has to pass); labels `known-defect` (defect of the cited code), `bug` (defect of new code), `enhancement`, `build`, `data`, `research` | [GitHub issues](https://github.com/yakupov/YB18-inds-framework/issues) |
| what changed in a release | [GitHub release notes](https://github.com/yakupov/YB18-inds-framework/releases), drafted by the Release workflow from the issues closed since the previous release |
| how the code differs from the cited code where it can change results: fixes, known limitations (each with its reason), structural changes that matter for comparisons | [docs/changes-from-cited-code.md](docs/changes-from-cited-code.md) |
| how sameness with the cited code is checked, and the results | [docs/verification.md](docs/verification.md) |
| running the benchmarks: parameters, examples, comparable numbers | [inds-benchmarks/README.md](inds-benchmarks/README.md) |
| which thesis experiment uses which data and benchmark, and how to re-run it | [docs/reproducibility.md](docs/reproducibility.md) |
| origin of every ported file | [docs/provenance.md](docs/provenance.md) |
| format of the golden-master records | [docs/golden-records.md](docs/golden-records.md) |
| the data sets: list, format, sources, how to change them | [inds-benchmark-data/README.md](inds-benchmark-data/README.md), `MANIFEST.tsv` |
| terms and thesis labels of a package | its `package-info.java` |
| the contract of a type or method | its Javadoc |
| rules for changing the code | this file; agent-specific notes in `CLAUDE.md` |
| publishing a release | [docs/releasing.md](docs/releasing.md) |

Commit messages refer to issues by number; old messages refer to the former roadmap IDs F1–F23, which
[#16](https://github.com/yakupov/YB18-inds-framework/issues/16) maps to issues and commits.

## Licenses

MIT only: no source derived from LGPL / GPL code (e.g. MOEA Framework) and no third-party code without a clear
license; third-party libraries only as Maven dependencies with MIT-compatible licenses.
