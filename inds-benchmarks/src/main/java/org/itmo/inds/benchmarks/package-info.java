/**
 * JMH benchmarks of the thesis experiments, on the data sets of module {@code inds-benchmark-data}.
 *
 * <table>
 * <caption>Benchmarks</caption>
 * <tr><th>Benchmark</th><th>Measures</th><th>Parameters</th></tr>
 * <tr><td>{@link org.itmo.inds.benchmarks.InsertionBenchmark}</td>
 *     <td>one insertion into a population snapshot (thesis Chapter 3)</td>
 *     <td>{@code algorithm} ({@link org.itmo.inds.benchmarks.InsertionAlgorithm}: DC, DC1, DC2, Level, ENLU
 *         population), {@code snapshot} ({@code path#id})</td></tr>
 * <tr><td>{@link org.itmo.inds.benchmarks.TwoDimInsertionBenchmark}</td>
 *     <td>one insertion into the 2D population</td>
 *     <td>{@code snapshot} (two objectives)</td></tr>
 * <tr><td>{@link org.itmo.inds.benchmarks.TwoDimSequenceBenchmark}</td>
 *     <td>inserting all points of a synthetic problem into an empty population (thesis Chapter 2)</td>
 *     <td>{@code algorithm} ({@link org.itmo.inds.benchmarks.TwoDimSequenceAlgorithm}: 2D, ENLU), {@code problem}
 *         ({@link org.itmo.inds.testdata.SyntheticProblem}), {@code size}</td></tr>
 * <tr><td>{@link org.itmo.inds.benchmarks.ConcurrentInsertionBenchmark}</td>
 *     <td>inserting a sequence of points with several threads (thesis Chapter 4)</td>
 *     <td>{@code algorithm} ({@link org.itmo.inds.benchmarks.ConcurrentAlgorithm}: Level, Coarse, Fine, Fine with
 *         rank hint, CAS, CAS one by one), {@code sequence} (path), {@code threads}</td></tr>
 * </table>
 *
 * <p>Populations are built from the true non-domination fronts of the data ({@link
 * org.itmo.inds.benchmarks.BenchmarkInput}), outside the measured time; populations that an insertion changes are
 * rebuilt before every measured invocation. Settings: average time in microseconds, 12 warm-up and 4 measurement
 * iterations of 1 s, 3 forks (the Chapter 2 benchmark: 5 and 5 iterations, 2 forks).
 *
 * <p>The concurrent driver ({@link org.itmo.inds.benchmarks.ConcurrentInsertionDriver},
 * {@link org.itmo.inds.benchmarks.ConcurrentInsertionData}) is the driver of the cited benchmarks (nds2018Cbenchmarks)
 * with new class names. Two adaptations: points are inserted as {@code FitnessAndCdIndividual} (the cited code used a
 * point class that no longer exists), and the Fine slot of the data holder has the type {@code ManagedPopulation},
 * because there are two Fine classes. Its method and slot names are the original ones: {@code JFBY} is Level,
 * {@code TsJFBY} Coarse, {@code SyncJFBY} / {@code levelLockJFBYPopulation} Fine, {@code CJFBY} CAS and
 * {@code CJFBYAlt} CAS one by one.
 */
package org.itmo.inds.benchmarks;
