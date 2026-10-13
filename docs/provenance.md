# Provenance

Where every file that was not written for this repository comes from. Ported files are the legacy files with only
the `package` / `import` lines and the class names changed (the original class name is given below), plus the fixes
listed in [changes-from-cited-code.md](changes-from-cited-code.md). How this was checked: [verification.md](verification.md).

Legacy repositories (all by I. Yakupov, unchanged, cited by the thesis and the papers):

| Short | Repository | Ref | Commit |
|---|---|---|---|
| nds2018 r2019.2 | [yakupov/nds2018](https://github.com/yakupov/nds2018/tree/r2019.2) | `r2019.2` (default branch) | `dfe4374fd0306f59b013ca3f5830a610b8942a0e` |
| nds2018 cd1 | [yakupov/nds2018](https://github.com/yakupov/nds2018/tree/r2018.9.1-cd1) | `r2018.9.1-cd1` (cited in thesis Ch. 4) | `bb69f72c9ca40548b1735c5eb639d8b275a55754` |
| nds2016 | [yakupov/nds2016](https://github.com/yakupov/nds2016) | `master` (cited in thesis Ch. 3) | `06430e71a2944aceb74a70094996731846b62895` |
| nds2018benchmarks | [yakupov/nds2018benchmarks](https://github.com/yakupov/nds2018benchmarks) | `master` | `62608bc893ac10185221718b47a2332f414fd521` |
| nds2018Cbenchmarks | [yakupov/nds2018Cbenchmarks](https://github.com/yakupov/nds2018Cbenchmarks) | `master` | `e210580913b480c6940720c444be515ff386d384` |

nds2018 paths are relative to `src/main/java/ru/ifmo/nds/` (tests: `src/test/java/ru/ifmo/nds/`).

## inds-core (`org.itmo.inds`)

| File | Origin | Legacy path (class) |
|---|---|---|
| `api/Individual` | nds2018 r2019.2 | `IIndividual` |
| `api/ManagedPopulation` | nds2018 r2019.2 | `IManagedPopulation` |
| `api/NonDominationLevel` | nds2018 r2019.2 | `INonDominationLevel` |
| `api/PopulationSnapshot` | nds2018 r2019.2 | `PopulationSnapshot` |
| `api/FitnessAndCdIndividual` | nds2018 r2019.2 | `impl/FitnessAndCdIndividual` |
| `api/RankedIndividual` | nds2018 r2019.2 | `impl/RankedIndividual` |
| `sorting/JfbSorter` | nds2018 r2019.2 | `dcns/sorter/JFB2014` |
| `sorting/IncrementalJfbSorter` | nds2018 r2019.2 | `dcns/sorter/IncrementalJFB` |
| `sorting/IndexedIndividual` | nds2018 r2019.2 | `dcns/sorter/IndexedIndividual` |
| `population/JfbyPopulation` | nds2018 r2019.2 | `dcns/jfby/JFBYPopulation` |
| `population/JfbyNonDominationLevel` | nds2018 r2019.2 | `dcns/jfby/JFBYNonDominationLevel` |
| `population/CoarseLockJfbyPopulation` | nds2018 r2019.2 | `dcns/jfby/TotalSyncJFBYPopulation` |
| `population/FineLockJfbyPopulation` | nds2018 **cd1** | `dcns/concurrent/LevelLockJFBYPopulation` (thesis version) |
| `population/FineLockJfbyPopulationRankHint` | nds2018 r2019.2 | `dcns/concurrent/LevelLockJFBYPopulation` (2019 revision) |
| `population/CasJfbyPopulation` | nds2018 **cd1** | `dcns/concurrent/CJFBYPopulation` (thesis version) |
| `twod/TwoDimTreapPopulation` | nds2016 | `PPSN2017Test/src/main/java/ru/itmo/nds/reference/treap2015/TreapPopulation` |
| `twod/TwoDimTreap` | nds2016 | `…/treap2015/Treap` |
| `util/Utils`, `util/SortedObjectives`, `util/RankedPopulation`, `util/AscLexSortComparator`, `util/ObjectiveComparator` | nds2018 r2019.2 | `util/` (same names) |
| `util/RankSorting` | nds2018 r2019.2 | `util/RankedIndividual` (renamed: clashes with `impl.RankedIndividual`) |
| `util/median/QuickSelect`, `util/median/MedianOfMediansQS` | nds2018 r2019.2 | `util/median/` (same names) |

The 2D classes were refactored after 0.1.1 (cleanup; the point type `…/treap2015/Double2DIndividual` became
`twod/TwoDimBucket`, which holds the `Individual`s with equal coordinates); the cited version is in the tag
`v0.1.1`.

Unified helpers: the cd1 classes (CAS, thesis Fine) run on the r2019.2 versions of the shared classes. Between the two
refs these differ only in imports and comments, except `Utils`: r2019.2 drops unused crowding-distance helpers and
has the iterative `dominates` with early exit, where cd1 called a recursive version from a utility library
(`NDSUtils.ComparisonUtils`, nds2016). Both give identical results; the iterative one is up to 2× faster for 6
objectives, so thesis-era timings of CAS and Fine may be slightly slower than this code.

## inds-baselines (`org.itmo.inds.baselines.enlu`)

| File | Origin | Legacy path (class) |
|---|---|---|
| `EnluPopulation` | nds2018 r2019.2 | `dcns/enlu/ENLUManagedPopulation` |
| `EnluNonDominationLevel` | nds2018 r2019.2 | `dcns/enlu/ENLUNonDominationLevel` |

## inds-testdata (`org.itmo.inds.testdata`)

`FrontStorage`, `RunConfiguration`, `Generation`, `DoublesGeneration`, `Front`, `DoublesAdditionProblem`: nds2016
`NDSUtils/src/main/java/ru/itmo/nds/front_storage/` (same names; their field names are the JSON format of the data
sets). `DataSets` and `FrontsAndAddends` are new.

## Tests

| File | Origin | Legacy path (class) |
|---|---|---|
| `inds-core` `population/ManagedPopulationCorrectnessTest` | nds2018 r2019.2 | `dcns/ManagedPopulationCorrectnessTest` |
| `inds-core` `population/JfbyPopulationCorrectnessTest` | nds2018 r2019.2 | `dcns/JFBYCorrectnessTest` |
| `inds-core` `population/CoarseLockJfbyPopulationCorrectnessTest` | nds2018 r2019.2 | `dcns/TotalSyncJFBYCorrectnessTest` |
| `inds-core` `population/FineLockJfbyPopulationRankHintCorrectnessTest` | nds2018 r2019.2 | `dcns/concurrent/LevelLockJFBYCorrectnessTest` |
| `inds-core` `population/FineLockJfbyPopulationCorrectnessTest` | nds2018 cd1 | `dcns/concurrent/LevelLockJFBYCorrectnessTest` (identical in both refs; for the thesis Fine) |
| `inds-core` `population/AbstractCasJfbyPopulationCorrectnessTest` | nds2018 r2019.2 | `dcns/concurrent/AbstractCJFBYCorrectnessTest` |
| `inds-core` `population/NormalCasJfbyPopulationCorrectnessTest` | nds2018 r2019.2 | `dcns/concurrent/NormalCJFBYCorrectnessTest` |
| `inds-core` `population/OneByOneCasJfbyPopulationCorrectnessTest` | nds2018 r2019.2 | `dcns/concurrent/OneByOneCJFBYCorrectnessTest` |
| `inds-baselines` `population/EnluPopulationCorrectnessTest` | nds2018 r2019.2 | `dcns/ENLUCorrectnessTest` |
| `inds-core` `sorting/JfbSorterNds2016Test` | nds2016 (adapted) | `IncrementalPPSN/src/test/java/ru/itmo/ppsn/PPSN2014CorrectnessTest` |
| `inds-core` `sorting/IncrementalJfbSorterNds2016Test` | nds2016 (adapted) | `IncrementalPPSN/src/test/java/ru/itmo/ppsn/IncrementalCorrectnessTest` |

The two nds2016 tests keep their bodies and data; they call the ported sorters through a small adapter
(`LegacySorterShim`) that offers the legacy `double[]` API. All other tests are new.

## Data sets (inds-benchmark-data)

Byte-identical copies; per-file sources, sizes, SHA-256 and git blob ids: [MANIFEST.tsv](../inds-benchmark-data/MANIFEST.tsv).

| Folder (under `org/itmo/inds/data/`) | Files | Origin | Legacy path |
|---|---|---|---|
| `snapshots/generational/` | 47 | nds2016 (the data of the thesis Ch. 3 figures) | `PPSN2016Test/src/main/resources/ru/itmo/nds/jmh/benchmarks/{dtlz3d,dtlzNd,uniform,zdt}/` |
| `snapshots/` (other folders) | 58 | nds2018benchmarks (byte-identical to nds2016 `PPSN2017Test/src/main/resources/ru/itmo/nds/jmh/benchmarks/`) | `src/main/resources/ru/itmo/nds/jmh/benchmarks/{constant,dtlz3d,dtlzNd,zdt}/` |
| `sequences/` | 46 | nds2018Cbenchmarks | `src/main/resources/ru/itmo/nds/jmh/benchmarks/{dtlz3d,dtlznd,zdt}/` |
