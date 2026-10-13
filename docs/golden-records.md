# Golden records: format and content

The golden-master tests (`inds-core`, package `org.itmo.inds.golden`) check that the ported algorithms behave exactly
like the code cited in the thesis. The legacy builds were run once on fixed inputs by a recorder; what they returned
and exposed was written to the *golden records*. The tests replay the same operations on the ported classes, write
records in the same format and compare them line by line; only the blocks of deliberate fixes may differ
([Changing the records](#changing-the-records)). The records are outputs of deterministic single-threaded
operations, not timings, so they are valid on any machine with JDK 17 or newer.

All files are gzipped UTF-8 text, test resources in `inds-core/src/test/resources/org/itmo/inds/golden/` (also in the
inds-core test-jar). Doubles are always written exactly, as the hexadecimal bit pattern
`Long.toHexString(Double.doubleToRawLongBits(x))`.

| File | Content |
|---|---|
| `inputs.txt.gz` | the 48 input sets (below) |
| `nds2018-r2019.2.txt.gz` | records of nds2018 `r2019.2`: DC, DC1, DC2, `addRankedMembers`, Level, Coarse, Fine (2019), ENLU population |
| `nds2018-r2018.9.1-cd1.txt.gz` | records of nds2018 `r2018.9.1-cd1` (cited in thesis Ch. 4): Fine (thesis), CAS, CAS one-by-one; also the classes it shares with `r2019.2`, with identical records |
| `nds2016.txt.gz` | records of nds2016 `master` (cited in thesis Ch. 3): 2D treap |
| `golden-divergences.txt` | the blocks that deliberate fixes changed ([Changing the records](#changing-the-records)) |

In code: `Source` (the three builds), `InputSet` / `InputsIO` (inputs), `Scenario`, `Target`, `RecordKind`,
`RecordBuffer` (writing), `GoldenRecords` (reading), `GoldenDivergences` (the divergence list), `StateEncoder`
(digests and dumps).

## Inputs file

```
# inds-golden-inputs v1
set <id> <family> <k> <n> <seed> <prefix> <smallExpected> <unlimited 0|1> <nProbes>
<k doubles>          n point lines, in insertion order
<k doubles>          nProbes probe lines
set ...
```

| Field | Meaning |
|---|---|
| `id` | unique name, e.g. `U-k3-n100`, `R-zdt1-k2-ds1-g5000` |
| `family` | `U` uniform random points in [0, 1)^k; `G` random integers in [0, 10)^k (ties, duplicates); `E` edge cases; `P` points on two or ten parallel layers; `R` NSGA-II population snapshots from the benchmark data sets plus the recorded next point |
| `k`, `n` | number of objectives, number of points |
| `seed` | generator seed; 0 for family `R` |
| `prefix` | points sorted or inserted before the incremental part of a scenario; for `R` the snapshot size (10 000) |
| `smallExpected` | expected population size of the eviction runs (about n / 2, at least 1; for `R` the snapshot size) |
| `unlimited` | whether populations also run with unlimited expected size (0 for `R`) |
| `nProbes` | number of probe points, whose ranks `determineRank` reports at the end of a population scenario |

The sets: `U` k = 2, 3, 5, 10 with n = 10, 100, 1000 and k = 3 with n = 10 000 (13 sets); `G` k = 2, 3, 5 with
n = 100, 1000 (6); `E` for k = 2 and 3: empty, single point, 20 equal points, a non-dominated pair, a dominance chain
inserted best-first and worst-first, 50 points on a simplex (14); `P` two and ten layers for k = 3, 5, 10, n = 1000
(6); `R` DTLZ1 (k = 3), DTLZ2 (k = 6), ZDT1 (k = 2) after 0, 5000 and 10 000 iterations, 10 001 points each (9).

## Records files

```
# inds-golden-records v1
# ...                                          comment lines from the recording (metadata), ignored
S <scenario> <block label> <input set> <params>
s <step> r=<return> n=<size> h=<digest>
D <step>
<dump lines>
/D
i <key> <value>
E <status>
S ...
```

A **block** is one scenario run of one algorithm on one input set: a start line `S`, step records, optional dumps and
additional results, and an end line `E`. Blocks are identified by their start line.

| Line | Meaning |
|---|---|
| `S <scenario> <block label> <input set> <params>` | block start. `block label`: the algorithm as named by the recorder (`DC`, `DC1`, `DC2`, `addRankedMembers-DC1`, `addRankedMembers-DC2`, `Level`, `Coarse`, `Fine`, `CAS`, `CAS-oneByOne`, `ENLU`, `TreapPopulation`; population labels with suffix `-clone` in the clone scenario). `params`: scenario parameters, e.g. `prefix=50`, `expected=unlimited`, `expected=50 cloneAfter=50`, `-` if none |
| `s <step> r=<return> n=<size> h=<digest>` | state after a step: the operation's return value (`-` if none), the size afterwards, and a digest of the observable state |
| `D <step>` ... `/D` | full dump of the same state (format below) |
| `i <key> <value>` | additional result: `probe-ranks` (ranks of the probe points), `ranked-population` (size and digest of `toRankedPopulation`), `level-sizes`, `clone-at`, `original-unchanged` (whether cloning left the original unchanged) |
| `E <status>` | block end: `ok`, `exc <exception class> at <step>` (the legacy code threw; the port must throw the same), or `skipped <reason>` |

**When records are written.** Step records after every step up to n = 1000, otherwise after every 100th step and the
last one. Full dumps after every step up to n = 50, after steps n / 2 and n up to n = 1000, never above. One-shot
scenarios (sorting all points at once) write step 0 and a dump up to n = 1000.

### Scenarios

| Scenario | Block labels | Operations |
|---|---|---|
| `dc-sort` | `DC` | sort all points at once (`JfbSorter.performNds`); records result order and ranks |
| `dc1-insert`, `dc2-insert` | `DC1`, `DC2` | sort the prefix with DC, then insert the remaining points one by one (`performIncrementalNds`) |
| `add-ranked-members` | `addRankedMembers-DC1`, `-DC2` | families `U` and `P` only: level 1 of the input (given rank 0) receives level 0 as addends, rank hint 0; `skipped fewer-than-two-levels` otherwise |
| `population` | `Level`, `Coarse`, `Fine`, `CAS`, `CAS-oneByOne`, `ENLU` | `addIndividual` for every point, with unlimited expected size (if the set has such a run) and with `smallExpected` (steady state with eviction); at the end the probe ranks, `toRankedPopulation` and the level sizes |
| `population-clone` | the same with `-clone` | sets with 2 to 1000 points: clone after the prefix, insert the rest into the clone only; records the clone and whether the original stayed unchanged |
| `treap-2d` | `TreapPopulation` | sets with k = 2: `addPoint` for every point |

Not recorded: random queries (`getRandomSolutions`), `toString`, multi-threaded runs (covered by the stress tests).

### Digest and dump

The state after a step is encoded canonically (`StateEncoder`): a sequence of elements, each a tag byte followed by
its fields as big-endian ints and longs, doubles as their raw bits. The digest is the first 64 bits of the SHA-256 of
that encoding, as 16 hex characters. A dump writes the same elements as text, one per line:

| Element | Dump line | Fields |
|---|---|---|
| state header | `p <size> <levels>` | size as reported; number of levels (-1 for sorter results) |
| level header | `l <rank> <members>` | followed by its members |
| population member | `m <payload> <objectives…> cd=<crowding distance>` | payload = index of the point in the input set (-1 if unknown) |
| point | `x <objectives…>` | for the 2D treap, which keeps raw arrays |
| sorter result entry | `r <payload> <rank>` | in result order |

Population levels are encoded in rank order with their members in `getMembers()` order. The 2D treap's levels are
encoded in in-order (increasing first objective), which does not depend on the random treap priorities.

## Changing the records

The records describe the cited code and are never changed; the recorder that produced them is not part of this
repository. A fix that changes behaviour lists the blocks it changes in `golden-divergences.txt`, one line per fix:
`<issue> <block labels> <input sets or *>`. Those blocks may differ; an entry that matches no differing block fails as
stale. The fixed behaviour itself is checked by the fix's own tests.
