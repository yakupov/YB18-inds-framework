/**
 * Golden-master tests: the ported algorithms must reproduce, bit for bit, what the legacy code returned and exposed on
 * fixed inputs. A recorder ran the legacy builds once; their records ("golden records") are test resources of this
 * package. The tests run the same operations on the ported classes, write records in the same format and compare
 * them line by line, legacy defects included (known defects: {@code docs/changes-from-cited-code.md}). The records are outputs of
 * deterministic single-threaded operations, not timings, so they do not depend on the machine. Human-readable
 * specification of the files and their content: {@code docs/golden-records.md}.
 *
 * <h2>Model</h2>
 * <ul>
 * <li>{@link org.itmo.inds.golden.Source}: a legacy build; one golden records file each.</li>
 * <li>{@link org.itmo.inds.golden.InputSet}: a named list of points in insertion order plus parameters, of one
 *     {@link org.itmo.inds.golden.InputSet.Family}; all 48 sets are in {@code inputs.txt.gz}.</li>
 * <li>{@link org.itmo.inds.golden.Scenario}: what is done with an input set and recorded.</li>
 * <li>{@link org.itmo.inds.golden.Target}: a recorded legacy algorithm (recorded name, source, scenarios); its
 *     blocks carry a block label derived from the recorded name.</li>
 * <li>Block: the records of one scenario run of one target on one input set.</li>
 * <li>{@link org.itmo.inds.golden.GoldenCheck.Replay}: runs a target's scenarios on the ported implementation and
 *     writes its blocks; the standard replays are in {@link org.itmo.inds.golden.Replays}.</li>
 * <li>{@link org.itmo.inds.golden.GoldenCheck}: compares the replayed blocks of a target with the golden ones; one
 *     test per target in {@link org.itmo.inds.golden.GoldenMasterTest} and in {@code EnluGoldenMasterTest} (module
 *     inds-baselines).</li>
 * </ul>
 *
 * <h2>Input sets</h2>
 * UNIFORM: k = 2, 3, 5, 10 and n = 10, 100, 1000 (and 10 000 for k = 3), 13 sets; GRID: k = 2, 3, 5 and n = 100, 1000,
 * 6 sets; EDGE: 7 cases for k = 2 and 3, 14 sets; LAYERS: two and ten layers for k = 3, 5, 10 with n = 1000, 6 sets;
 * SNAPSHOT: DTLZ1 (k = 3), DTLZ2 (k = 6), ZDT1 (k = 2) after 0, 5000 and 10 000 iterations, 9 sets of 10 001 points.
 * Each set also has 15-20 probe points.
 *
 * <h2>Files</h2>
 * Gzipped text, test resources of this package (also in the inds-core test-jar).
 * <ul>
 * <li>{@code inputs.txt.gz}: format in {@link org.itmo.inds.golden.InputsIO}.</li>
 * <li>{@code <source>.txt.gz}, one per {@link org.itmo.inds.golden.Source} (read by
 *     {@link org.itmo.inds.golden.GoldenRecords}): header {@code # inds-golden-records v1}, comment lines starting with
 *     {@code #} (from the recording), then blocks:
 * <pre>{@code
 * S <scenario> <block label> <inputSet> <params>    block start
 * s <step> r=<return> n=<size> h=<digest>     state after a step (digest: see StateEncoder)
 * D <step> ... /D                             full dump of the same state
 * i <key> <value>                             additional result (probe ranks, final summaries)
 * E <status>                                  block end: ok | exc <class> at <step> | skipped <reason>
 * }</pre>
 *     When step records and dumps are written: {@link org.itmo.inds.golden.RecordBuffer}.</li>
 * </ul>
 * Not recorded: random queries ({@code getRandomSolutions}), {@code toString}, multi-threaded runs (covered by the
 * stress tests).
 */
package org.itmo.inds.golden;
