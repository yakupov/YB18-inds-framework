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

## Releasing

Published to Maven Central under the group id `io.github.yakupov`: `inds-parent` (POM only), `inds-core` and
`inds-baselines`, each with sources and Javadoc. The test data and benchmark modules and `inds-core`'s test-jar
(golden records, test infrastructure) are not published. A release on Central is permanent: check before publishing.

### One-time setup (maintainer only)

1. **Central account.** Sign in to [central.sonatype.com](https://central.sonatype.com) with the GitHub account
   `yakupov`; the namespace `io.github.yakupov` is verified with it. Generate a user token (a username and password
   pair, not the login) for each place that uploads: one for `~/.m2/settings.xml` (server id `central`) if releasing
   by hand, one for GitHub.
2. **Signing key.** Central requires an OpenPGP signature (`.asc`) for every file and checks it against the public key
   on a key server. The maintainer's key has a signing subkey for CI, so the primary key never leaves the
   maintainer's machine:

   ```bash
   gpg --quick-add-key <FINGERPRINT> rsa4096 sign 2y                 # signing subkey, 2 years
   gpg --keyserver keyserver.ubuntu.com --send-keys <FINGERPRINT>   # publish the key with the new subkey
   gpg --armor --export-secret-subkeys <SUBKEY_ID>!                 # only that subkey (note the "!")
   ```

   If the subkey leaks, revoke it (`gpg --edit-key <FINGERPRINT>`, `key <n>`, `revkey`), send the key again and add a
   new one.
3. **GitHub environment.** Settings → Environments → New environment `release`: required reviewer `yakupov`;
   deployment branches: selected branches, `main`; environment secrets `CENTRAL_TOKEN_USERNAME`,
   `CENTRAL_TOKEN_PASSWORD` (the token for GitHub), `GPG_PRIVATE_KEY` (the exported subkey, armored text),
   `GPG_PASSPHRASE`. Jobs of other branches and pull requests never see these secrets.

### Release `X.Y.Z` from GitHub Actions

1. Actions → **Release** → Run workflow on `main`: `version` `X.Y.Z`; `next` defaults to `X.Y+1.0-SNAPSHOT`.
   `dry-run` builds and bundles without signing, uploading or pushing.
2. The workflow ([release.yml](.github/workflows/release.yml)) checks the inputs and that the tag is new, runs
   `mvn verify` with the release version, then waits for approval (environment `release`). After approval it commits
   "Release X.Y.Z", builds and signs the three artifacts, bundles them, uploads the bundle
   ([scripts/central-upload.py](scripts/central-upload.py)) and waits until Central has validated it. Only then it
   tags `vX.Y.Z`, commits the next development version and pushes both commits and the tag to `main` in one atomic
   push (as `github-actions[bot]`), and creates a draft GitHub release. It fails without uploading if `main` moved
   after the start.
3. Publish the validated deployment on central.sonatype.com (Publish → Deployments → Publish), or drop it. Then publish
   the draft release on GitHub. After a drop, delete the tag and the draft release, or release the next version.

### Release by hand (fallback)

```bash
mvn verify                                                    # all tests, on the code to release
mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
git commit -am "Release X.Y.Z" && git tag -a vX.Y.Z -m "Release X.Y.Z"
mvn -Prelease -Dmaven.test.skip=true -pl .,inds-core,inds-baselines deploy   # signs; writes target/central-staging
python scripts/central-bundle.py target/central-staging target/central-bundle.zip
```

The `release` profile deploys into the local directory `target/central-staging`, not to a server, and refuses to run
without `-Dmaven.test.skip=true` (that switch also keeps `inds-core`'s test-jar out). `central-bundle.py` checks that
every POM and jar has its signature and checksums and zips them with the layout Central expects. Upload
`target/central-bundle.zip` on central.sonatype.com (Publish → Publish Component), or with `central-upload.py` (token
in `CENTRAL_TOKEN_USERNAME` / `CENTRAL_TOKEN_PASSWORD`); wait for the validation, check the three components and
publish. Then push the commit and the tag, and set the next development version
(`mvn versions:set -DnewVersion=X.Y+1.0-SNAPSHOT -DgenerateBackupPoms=false`, commit, push).

Sonatype's `central-publishing-maven-plugin` is not used: on Windows with Maven 3.10 it writes zip entries with
backslashes and puts resolver files into the bundle, which Central rejects. The scripts build and upload the same
bundle on every platform.
